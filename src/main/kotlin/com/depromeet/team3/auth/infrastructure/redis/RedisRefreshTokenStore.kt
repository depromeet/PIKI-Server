package com.depromeet.team3.auth.infrastructure.redis

import com.depromeet.team3.auth.infrastructure.jwt.JwtProperties
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
            .set(key(userId), refreshToken, jwtProperties.refreshTokenExpirySeconds, TimeUnit.SECONDS)
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

    private fun key(userId: UUID) = "refresh:$userId"

    companion object {
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
