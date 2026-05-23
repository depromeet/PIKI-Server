package com.depromeet.piki.auth.infrastructure.redis

import com.depromeet.piki.auth.infrastructure.jwt.JwtProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.TimeUnit

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
        return result == 1L
    }

    // 1 유저당 1 refresh token 만 저장하는 설계. 같은 계정 다중 디바이스 로그인 시
    // 나중 로그인이 이전 토큰을 덮어쓰므로 이전 디바이스는 다음 refresh 시점에 강제 로그아웃된다.
    // 다중 디바이스 동시 로그인을 허용하려면 key 에 디바이스 식별자(jti / deviceId)를 추가한다.
    private fun key(userId: UUID) = "$KEY_PREFIX$userId"

    companion object {
        private const val KEY_PREFIX = "refresh:"

        private val CONSUME_SCRIPT =
            DefaultRedisScript<Long>().apply {
                setScriptText(
                    """
                    local stored = redis.call('GET', KEYS[1])
                    if stored == false then return 0 end
                    if stored ~= ARGV[1] then return 0 end
                    redis.call('DEL', KEYS[1])
                    return 1
                    """.trimIndent(),
                )
                setResultType(Long::class.java)
            }
    }
}
