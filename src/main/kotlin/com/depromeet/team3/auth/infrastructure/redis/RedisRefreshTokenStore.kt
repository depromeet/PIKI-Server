package com.depromeet.team3.auth.infrastructure.redis

import com.depromeet.team3.auth.infrastructure.jwt.JwtProperties
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
            .set(key(userId), refreshToken, jwtProperties.refreshTokenExpiry.toMillis(), TimeUnit.MILLISECONDS)
    }

    override fun get(userId: UUID): String? = redisTemplate.opsForValue().get(key(userId))

    override fun delete(userId: UUID) {
        redisTemplate.delete(key(userId))
    }

    override fun consumeIfMatches(
        userId: UUID,
        token: String,
    ): Boolean {
        val result =
            redisTemplate.execute(
                CONSUME_SCRIPT,
                listOf(key(userId)),
                token,
            )
        // Lua script 반환값 의미:
        //   1 = 정상 매치 + DEL (다음 R 발급으로 이어짐)
        //   0 = 키 없음 또는 mismatch. mismatch 인 경우 살아있던 R 도 같이 DEL (family invalidation)
        //  -1 = mismatch 로 family invalidation 트리거됨 (도난 의심)
        // -1 은 호출자 입장에선 거부 (false) 와 같지만, 도난 감지 시점을 로깅으로 남긴다.
        // 향후 사용자 알림 hook 의 사전 단계.
        if (result == -1L) {
            logger.info("refresh token reuse detected — family invalidated. userId={}", userId)
        }
        return result == 1L
    }

    // 1 유저당 1 refresh token 만 저장하는 설계. 같은 계정 다중 디바이스 로그인 시
    // 나중 로그인이 이전 토큰을 덮어쓰므로 이전 디바이스는 다음 refresh 시점에 강제 로그아웃된다.
    // 다중 디바이스 동시 로그인을 허용하려면 key 에 디바이스 식별자(jti / deviceId)를 추가한다.
    private fun key(userId: UUID) = "$KEY_PREFIX$userId"

    companion object {
        private const val KEY_PREFIX = "refresh:"

        // OAuth 2.0 RFC 6819 / 8252 의 "Refresh Token Rotation + Family Invalidation" 패턴.
        // 단순 거부에 그치면 공격자가 최신 R 을 먼저 써서 회전시킨 뒤 정상 사용자만 튕기는
        // 경로가 살아남는다. mismatch (= 재사용 시도) 감지 시 살아있던 R 도 같이 삭제해
        // 공격자·정상 사용자 모두 끊고 재로그인을 강제한다.
        // 반환값: 1 = 정상 매치 + DEL, 0 = 키 없음 (이미 소비/만료), -1 = mismatch → family invalidation
        private val CONSUME_SCRIPT =
            DefaultRedisScript<Long>().apply {
                setScriptText(
                    """
                    local stored = redis.call('GET', KEYS[1])
                    if stored == false then return 0 end
                    if stored ~= ARGV[1] then
                        redis.call('DEL', KEYS[1])
                        return -1
                    end
                    redis.call('DEL', KEYS[1])
                    return 1
                    """.trimIndent(),
                )
                setResultType(Long::class.java)
            }
    }
}
