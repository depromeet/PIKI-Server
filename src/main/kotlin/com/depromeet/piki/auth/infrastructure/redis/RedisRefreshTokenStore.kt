package com.depromeet.piki.auth.infrastructure.redis

import com.depromeet.piki.auth.infrastructure.jwt.JwtProperties
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.TimeUnit

private val logger = LoggerFactory.getLogger(RedisRefreshTokenStore::class.java)

@Component
class RedisRefreshTokenStore(
    private val redisTemplate: StringRedisTemplate,
    private val jwtProperties: JwtProperties,
) : RefreshTokenStore {
    override fun save(
        userId: UUID,
        refreshToken: String,
    ) {
        redisTemplate
            .opsForValue()
            .set(currentKey(userId), refreshToken, jwtProperties.refreshTokenExpiry.toMillis(), TimeUnit.MILLISECONDS)
    }

    override fun get(userId: UUID): String? = redisTemplate.opsForValue().get(currentKey(userId))

    override fun delete(userId: UUID) {
        redisTemplate.delete(listOf(currentKey(userId), graceKey(userId)))
    }

    override fun rotateOrReplay(
        userId: UUID,
        presented: String,
        candidateRefreshToken: String,
    ): RefreshOutcome {
        val result =
            redisTemplate.execute(
                REFRESH_SCRIPT,
                listOf(currentKey(userId), graceKey(userId)),
                presented,
                candidateRefreshToken,
                jwtProperties.refreshTokenExpiry.toMillis().toString(),
                jwtProperties.refreshTokenGrace.toMillis().toString(),
            ) ?: error("refresh Lua script 가 null 을 반환했다 (userId=$userId)")

        // Lua 반환 코드 → 도메인 결과. "P:" 는 grace replay 로 돌려줄 토큰을 접두사 뒤에 싣는다.
        return when {
            result == ROTATED -> RefreshOutcome.Rotated
            result.startsWith(REPLAY_PREFIX) -> RefreshOutcome.Replayed(result.removePrefix(REPLAY_PREFIX))
            result == EXPIRED -> RefreshOutcome.Expired
            result == REUSE -> {
                // warn 레벨: 시스템 fail 아닌 보안 의심 이벤트. info 보다 가시성 ↑, error 는 alert fatigue 위험 +
                // 시스템 정상이라 의미 안 맞음. PIKI 로그 레벨 정책의 "정상 흐름 아닌 의심 이벤트" 범주.
                logger.warn("refresh token reuse detected — family invalidated. userId={}", userId)
                RefreshOutcome.ReuseDetected
            }
            else -> error("refresh Lua script 가 예상 못한 값을 반환했다: $result (userId=$userId)")
        }
    }

    // 1 유저당 1 refresh token 만 저장하는 설계. 같은 계정 다중 디바이스 로그인 시
    // 나중 로그인이 이전 토큰을 덮어쓰므로 이전 디바이스는 다음 refresh 시점에 강제 로그아웃된다.
    // 다중 디바이스 동시 로그인을 허용하려면 key 에 디바이스 식별자(jti / deviceId)를 추가한다.
    private fun currentKey(userId: UUID) = "$KEY_PREFIX$userId"

    // 회전 직후 "옛 토큰|새 토큰" 매핑을 grace TTL 동안 보관 — 동시 요청의 멱등 replay 근거.
    private fun graceKey(userId: UUID) = "$GRACE_PREFIX$userId"

    companion object {
        private const val KEY_PREFIX = "refresh:"
        private const val GRACE_PREFIX = "refresh:grace:"

        private const val ROTATED = "R"
        private const val EXPIRED = "0"
        private const val REUSE = "-1"
        private const val REPLAY_PREFIX = "P:"

        // OAuth 2.0 RFC 6819 / 8252 의 "Refresh Token Rotation + Family Invalidation" + Auth0 식 reuse interval.
        //
        // 토큰 생성은 앱(JwtProvider)이 하므로 "consume → generate → save" 가 본래 다단계라 동시 요청에 race 가 난다.
        // 그래서 호출자가 candidate(ARGV[2]) 를 미리 만들어 넘기고, 이 스크립트가 회전·grace·replay·무효화 판정을
        // 통째로 원자 수행한다. Redis 싱글스레드 직렬화 덕에 동시 N개 중 먼저 든 쪽이 회전 승자가 되고 grace 를
        // 쓰며, 나머지는 같은 스크립트 안에서 승자 토큰을 replay 로 돌려받아 모두 같은 새 토큰으로 수렴한다.
        //
        // KEYS[1]=current, KEYS[2]=grace
        // ARGV[1]=presented(제시 토큰), ARGV[2]=candidate(새 토큰), ARGV[3]=현재토큰 TTL(ms), ARGV[4]=grace TTL(ms)
        //
        // grace 값 포맷 "<old>|<new>": JWT 는 base64url(`[A-Za-z0-9_-]`)·점(.)뿐이라 '|' 와 충돌하지 않는다.
        //
        // 반환:
        //   "R"        현재 토큰과 일치 → 회전 (current=candidate, grace="presented|candidate")
        //   "P:<tok>"  grace 의 old 가 presented 와 일치 → 멱등 replay (이미 발급된 new 를 반환). 회전·무효화 없음
        //   "0"        현재 토큰 없음 + grace 도 없음 → 만료/이미 소비 → 거부
        //   "-1"       현재 토큰은 있으나 불일치 + grace 밖 → 재사용 의심 → current·grace 둘 다 DEL (family invalidation)
        private val REFRESH_SCRIPT =
            DefaultRedisScript<String>().apply {
                setScriptText(
                    """
                    local cur = redis.call('GET', KEYS[1])
                    if cur == ARGV[1] then
                        redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
                        redis.call('SET', KEYS[2], ARGV[1] .. '|' .. ARGV[2], 'PX', ARGV[4])
                        return 'R'
                    end
                    local g = redis.call('GET', KEYS[2])
                    if g ~= false then
                        local sep = string.find(g, '|', 1, true)
                        if sep ~= nil and string.sub(g, 1, sep - 1) == ARGV[1] then
                            return 'P:' .. string.sub(g, sep + 1)
                        end
                    end
                    if cur == false then return '0' end
                    redis.call('DEL', KEYS[1])
                    redis.call('DEL', KEYS[2])
                    return '-1'
                    """.trimIndent(),
                )
                setResultType(String::class.java)
            }
    }
}
