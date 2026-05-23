package com.depromeet.piki.auth.infrastructure.redis

import com.depromeet.piki.support.IntegrationTestSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RedisRefreshTokenStoreIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var store: RefreshTokenStore

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @Test
    fun `save 한 refresh token 을 get 으로 조회할 수 있다`() {
        val userId = UUID.randomUUID()
        val token = "refresh-token-for-$userId"

        store.save(userId, token)

        assertEquals(token, store.get(userId))

        store.delete(userId)
    }

    @Test
    fun `delete 후 get 은 null 을 반환한다`() {
        val userId = UUID.randomUUID()
        store.save(userId, "some-token")

        store.delete(userId)

        assertNull(store.get(userId))
    }

    @Test
    fun `같은 userId 로 두 번 save 하면 두번째 값이 첫번째를 덮어쓴다`() {
        val userId = UUID.randomUUID()
        store.save(userId, "first")
        store.save(userId, "second")

        assertEquals("second", store.get(userId))

        store.delete(userId)
    }

    @Test
    fun `존재하지 않는 userId 로 get 하면 null 을 반환한다`() {
        assertNull(store.get(UUID.randomUUID()))
    }

    @Test
    fun `key prefix 는 refresh 콜론 패턴이다`() {
        val userId = UUID.randomUUID()
        store.save(userId, "token")

        assertNotNull(redisTemplate.opsForValue().get("refresh:$userId"))

        store.delete(userId)
    }
}
