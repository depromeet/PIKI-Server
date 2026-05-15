package com.depromeet.team3.auth.infrastructure.redis

import com.depromeet.team3.auth.infrastructure.jwt.JwtProperties
import org.springframework.data.redis.core.StringRedisTemplate
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

    private fun key(userId: UUID) = "refresh:$userId"
}
