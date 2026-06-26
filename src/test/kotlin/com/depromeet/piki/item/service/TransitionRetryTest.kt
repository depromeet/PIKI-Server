package com.depromeet.piki.item.service

import org.springframework.dao.QueryTimeoutException
import org.springframework.transaction.TransactionSystemException
import java.sql.SQLTransactionRollbackException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// 전이 write 의 일시 DB 오류 인라인 재시도 정책 — 일시면 짧게 재시도, 영구면 즉시 전파, 상한 도달이면 마지막 예외 전파.
// commit(flush) 시점 장애가 TransactionSystemException 으로 감싸지거나 raw SQLTransientException 으로 와도 일시로 본다.
class TransitionRetryTest {
    private val retry = TransitionRetry()

    @Test
    fun `첫 시도에 성공하면 block 을 한 번만 호출하고 결과를 돌려준다`() {
        val calls = AtomicInteger(0)
        val result = retry.execute { calls.incrementAndGet(); "ok" }
        assertEquals("ok", result)
        assertEquals(1, calls.get())
    }

    @Test
    fun `일시 DB 오류가 상한 안에서 가시면 재시도해 성공값을 돌려준다`() {
        val calls = AtomicInteger(0)
        val result =
            retry.execute {
                if (calls.incrementAndGet() < 3) throw QueryTimeoutException("lock wait timeout")
                "ok"
            }
        assertEquals("ok", result)
        assertEquals(3, calls.get()) // 2회 실패 후 3번째 성공
    }

    @Test
    fun `일시 DB 오류가 상한까지 계속되면 마지막 예외를 던지고 재시도를 상한에서 멈춘다`() {
        val calls = AtomicInteger(0)
        assertFailsWith<QueryTimeoutException> {
            retry.execute<Unit> {
                calls.incrementAndGet()
                throw QueryTimeoutException("deadlock")
            }
        }
        assertEquals(3, calls.get()) // MAX_ATTEMPTS
    }

    @Test
    fun `commit 시점 장애가 TransactionSystemException 으로 감싸져도 일시로 보고 재시도한다`() {
        val calls = AtomicInteger(0)
        val result =
            retry.execute {
                if (calls.incrementAndGet() < 2) {
                    throw TransactionSystemException("could not commit", SQLTransactionRollbackException("deadlock found"))
                }
                "ok"
            }
        assertEquals("ok", result)
        assertEquals(2, calls.get())
    }

    @Test
    fun `SQLTransientException 이 cause 사슬에 있으면 일시로 본다`() {
        val calls = AtomicInteger(0)
        val result =
            retry.execute {
                if (calls.incrementAndGet() < 2) {
                    throw RuntimeException("wrap", SQLTransactionRollbackException("deadlock 40001"))
                }
                "ok"
            }
        assertEquals("ok", result)
        assertEquals(2, calls.get())
    }

    @Test
    fun `영구 오류는 재시도 없이 즉시 던진다`() {
        val calls = AtomicInteger(0)
        assertFailsWith<IllegalStateException> {
            retry.execute<Unit> {
                calls.incrementAndGet()
                throw IllegalStateException("이미 전이됨")
            }
        }
        assertEquals(1, calls.get())
    }
}
