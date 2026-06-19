package com.depromeet.piki.auth.infrastructure.redis

import com.depromeet.piki.support.IntegrationTestSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RedisRefreshTokenStoreIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var store: RedisRefreshTokenStore

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

    @Test
    fun `rotateOrReplay - 현재 토큰과 일치하면 Rotated 이고 새 토큰으로 회전된다`() {
        val userId = UUID.randomUUID()
        store.save(userId, "A")

        val outcome = store.rotateOrReplay(userId, presented = "A", candidateRefreshToken = "B")

        assertIs<RefreshOutcome.Rotated>(outcome)
        assertEquals("B", store.get(userId))

        store.delete(userId)
    }

    @Test
    fun `rotateOrReplay - grace 창 안에 옛 토큰으로 다시 오면 Replayed 로 같은 새 토큰을 반환한다`() {
        val userId = UUID.randomUUID()
        store.save(userId, "A")
        store.rotateOrReplay(userId, presented = "A", candidateRefreshToken = "B") // 회전: current=B, grace=A|B

        // 옛 토큰 A 로 동시 재요청 — candidate C 는 버려지고 승자 토큰 B 가 멱등 반환돼야 한다
        val outcome = store.rotateOrReplay(userId, presented = "A", candidateRefreshToken = "C")

        assertIs<RefreshOutcome.Replayed>(outcome)
        assertEquals("B", outcome.refreshToken)
        assertEquals("B", store.get(userId)) // current 는 그대로 B (재회전 없음)

        store.delete(userId)
    }

    @Test
    fun `rotateOrReplay - 저장된 토큰이 없으면 Expired 이다`() {
        val outcome = store.rotateOrReplay(UUID.randomUUID(), presented = "A", candidateRefreshToken = "B")

        assertIs<RefreshOutcome.Expired>(outcome)
    }

    @Test
    fun `rotateOrReplay - grace 밖에서 옛 토큰을 재사용하면 ReuseDetected 이고 current 가 무효화된다`() {
        val userId = UUID.randomUUID()
        store.save(userId, "A")
        store.rotateOrReplay(userId, presented = "A", candidateRefreshToken = "B") // current=B, grace=A|B

        // grace TTL 경과 시뮬레이션 — grace 키만 제거 (실시간 10초 대기 회피)
        redisTemplate.delete("refresh:grace:$userId")

        val outcome = store.rotateOrReplay(userId, presented = "A", candidateRefreshToken = "C")

        assertIs<RefreshOutcome.ReuseDetected>(outcome)
        assertNull(store.get(userId)) // family invalidation: 살아있던 current(B) 도 삭제

        store.delete(userId)
    }

    @Test
    fun `delete 는 grace 키도 함께 지운다`() {
        val userId = UUID.randomUUID()
        store.save(userId, "A")
        store.rotateOrReplay(userId, presented = "A", candidateRefreshToken = "B") // grace=A|B 기록됨

        store.delete(userId)

        assertNull(redisTemplate.opsForValue().get("refresh:grace:$userId"))
    }

    @Test
    fun `rotateOrReplay - 같은 옛 토큰 동시 요청은 한 번만 회전하고 모두 같은 새 토큰으로 수렴한다`() {
        val userId = UUID.randomUUID()
        store.save(userId, "A")

        val threads = 8
        val executor = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        try {
            val futures =
                (0 until threads).map { i ->
                    executor.submit<RefreshOutcome> {
                        ready.countDown()
                        start.await()
                        // 각 스레드가 서로 다른 candidate 를 제시 — 승자의 것만 채택돼야 한다
                        store.rotateOrReplay(userId, presented = "A", candidateRefreshToken = "cand-$i")
                    }
                }
            ready.await()
            start.countDown()
            val outcomes = futures.map { it.get(10, TimeUnit.SECONDS) }
            executor.shutdown()

            // 정확히 하나만 Rotated, 나머지는 Replayed
            assertEquals(1, outcomes.count { it is RefreshOutcome.Rotated })
            assertEquals(threads - 1, outcomes.count { it is RefreshOutcome.Replayed })

            // 모든 요청이 인지한 "새 토큰"이 저장된 current 와 동일하게 수렴
            val winner = store.get(userId)
            assertNotNull(winner)
            val seenTokens =
                outcomes
                    .map { if (it is RefreshOutcome.Replayed) it.refreshToken else winner }
                    .toSet()
            assertEquals(setOf(winner), seenTokens)
        } finally {
            executor.shutdownNow()
            store.delete(userId)
        }
    }
}
