package com.depromeet.piki.notification.sse

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 레지스트리는 SSE 연결의 멤버십만 다루므로 Spring·DB 없이 단위로 망라한다(emitter 는 식별자로만 쓰고 전송하지 않는다).
class SseEmitterRegistryTest {
    private val registry = SseEmitterRegistry()

    @Test
    fun `register 한 emitter 가 emittersOf 로 조회된다`() {
        val userId = UUID.randomUUID()
        val emitter = SseEmitter()

        registry.register(userId, emitter)

        assertEquals(listOf(emitter), registry.emittersOf(userId))
    }

    @Test
    fun `한 유저가 여러 탭으로 접속하면 emitter 가 모두 보관된다`() {
        val userId = UUID.randomUUID()
        val first = SseEmitter()
        val second = SseEmitter()

        registry.register(userId, first)
        registry.register(userId, second)

        assertEquals(listOf(first, second), registry.emittersOf(userId))
    }

    @Test
    fun `unregister 하면 해당 emitter 만 빠지고 나머지는 남는다`() {
        val userId = UUID.randomUUID()
        val keep = SseEmitter()
        val drop = SseEmitter()
        registry.register(userId, keep)
        registry.register(userId, drop)

        registry.unregister(userId, drop)

        assertEquals(listOf(keep), registry.emittersOf(userId))
    }

    @Test
    fun `마지막 emitter 를 unregister 하면 그 유저의 연결이 비워진다`() {
        val userId = UUID.randomUUID()
        val emitter = SseEmitter()
        registry.register(userId, emitter)

        registry.unregister(userId, emitter)

        assertTrue(registry.emittersOf(userId).isEmpty())
        var visited = 0
        registry.forEach { _, _ -> visited++ }
        assertEquals(0, visited)
    }

    @Test
    fun `등록되지 않은 유저의 emittersOf 는 빈 리스트다`() {
        assertTrue(registry.emittersOf(UUID.randomUUID()).isEmpty())
    }

    @Test
    fun `없는 emitter 를 unregister 해도 예외 없이 무시된다`() {
        val userId = UUID.randomUUID()
        registry.register(userId, SseEmitter())

        registry.unregister(userId, SseEmitter())
        registry.unregister(UUID.randomUUID(), SseEmitter())

        assertEquals(1, registry.emittersOf(userId).size)
    }

    @Test
    fun `forEach 는 모든 유저의 모든 emitter 쌍을 순회한다`() {
        val userA = UUID.randomUUID()
        val userB = UUID.randomUUID()
        registry.register(userA, SseEmitter())
        registry.register(userA, SseEmitter())
        registry.register(userB, SseEmitter())

        val visited = mutableListOf<UUID>()
        registry.forEach { userId, _ -> visited.add(userId) }

        assertEquals(2, visited.count { it == userA })
        assertEquals(1, visited.count { it == userB })
    }

    @Test
    @Timeout(15, unit = TimeUnit.SECONDS)
    fun `동시 register 와 unregister 가 유실이나 예외 없이 처리된다`() {
        val userId = UUID.randomUUID()
        val count = 50
        val emitters = List(count) { SseEmitter() }

        runConcurrently(count) { i -> registry.register(userId, emitters[i]) }
        assertEquals(count, registry.emittersOf(userId).size)

        runConcurrently(count) { i -> registry.unregister(userId, emitters[i]) }
        assertTrue(registry.emittersOf(userId).isEmpty())
    }

    // count 개의 작업을 동시에 출발시킨다(2단계 래치). 풀 크기를 count 와 같게 둬야 모든 작업이 동시에
    // start 래치까지 도달한다 — 풀이 작으면 일부 작업이 큐에 묶여 ready 가 0 에 못 닿는 기아 데드락이 난다.
    private fun runConcurrently(
        count: Int,
        action: (Int) -> Unit,
    ) {
        val executor = Executors.newFixedThreadPool(count)
        val ready = CountDownLatch(count)
        val start = CountDownLatch(1)
        try {
            repeat(count) { i ->
                executor.submit {
                    ready.countDown()
                    start.await()
                    action(i)
                }
            }
            ready.await()
            start.countDown()
            executor.shutdown()
            check(executor.awaitTermination(10, TimeUnit.SECONDS)) { "동시 작업이 시간 내 끝나지 않았다" }
        } finally {
            executor.shutdownNow()
        }
    }
}
