package com.depromeet.team3.product.service.gemini

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeminiRetryTest {

    // 실제 대기 없이 재시도 횟수·분류만 검증하기 위해 sleep 을 no-op 으로 주입.
    private val retry = GeminiRetry(sleep = {})

    @Test
    fun `첫 시도에 성공하면 재시도하지 않고 결과를 반환한다`() {
        var calls = 0

        val result =
            retry.execute {
                calls++
                "ok"
            }

        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test
    fun `RETRYABLE 예외가 계속 나면 최대 3회 시도 후 마지막 예외를 던진다`() {
        var calls = 0

        assertFailsWith<GeminiApiException> {
            retry.execute {
                calls++
                throw GeminiApiException.emptyResponse()
            }
        }

        assertEquals(3, calls)
    }

    @Test
    fun `RETRYABLE 예외 후 재시도에서 성공하면 그 결과를 반환한다`() {
        var calls = 0

        val result =
            retry.execute {
                calls++
                if (calls < 2) throw GeminiApiException.emptyResponse()
                "recovered"
            }

        assertEquals("recovered", result)
        assertEquals(2, calls)
    }

    @Test
    fun `SERVER_ERROR 예외는 재시도하지 않고 즉시 던진다`() {
        var calls = 0

        assertFailsWith<GeminiApiException> {
            retry.execute {
                calls++
                throw GeminiApiException.parseError(RuntimeException("깨진 JSON"))
            }
        }

        assertEquals(1, calls)
    }

    @Test
    fun `GeminiApiException 이 아닌 예외는 재시도하지 않고 즉시 던진다`() {
        var calls = 0

        assertFailsWith<IllegalStateException> {
            retry.execute {
                calls++
                throw IllegalStateException("boom")
            }
        }

        assertEquals(1, calls)
    }
}
