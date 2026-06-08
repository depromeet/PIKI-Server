package com.depromeet.piki.auth.infrastructure.redis

import com.depromeet.piki.support.IntegrationTestSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// @Primary stub 으로 가려진 실제 Redis 구현을 concrete 타입으로 직접 주입해 검증한다
// (RedisRefreshTokenStoreIntegrationTest 와 동일 패턴).
class RedisWithdrawnTokenStoreIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var store: RedisWithdrawnTokenStore

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @Test
    fun `markWithdrawn 후 isWithdrawn 은 true 다`() {
        val userId = UUID.randomUUID()

        store.markWithdrawn(userId)

        assertTrue(store.isWithdrawn(userId))
        redisTemplate.delete("withdrawn:$userId")
    }

    @Test
    fun `markWithdrawn 하지 않은 유저는 isWithdrawn 이 false 다`() {
        assertFalse(store.isWithdrawn(UUID.randomUUID()))
    }

    @Test
    fun `markWithdrawn 은 TTL 을 설정한다 (만료 시 자동 정리)`() {
        val userId = UUID.randomUUID()

        store.markWithdrawn(userId)

        val ttl = redisTemplate.getExpire("withdrawn:$userId", TimeUnit.SECONDS)
        assertTrue(ttl > 0, "denylist 마커에 TTL 이 설정되어야 한다")
        redisTemplate.delete("withdrawn:$userId")
    }
}
