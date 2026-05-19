package com.depromeet.team3.product.service.gemini

import com.depromeet.team3.common.exception.ErrorCategory
import org.slf4j.LoggerFactory
import kotlin.random.Random

/**
 * Gemini 호출의 일시 장애를 지수 백오프로 재시도한다.
 *
 * `RETRYABLE` 카테고리의 [GeminiApiException](5xx · 429 · 408 · 네트워크 타임아웃 · 빈 응답)만
 * 재시도하고, `SERVER_ERROR` 등 재시도해도 의미 없는 실패는 즉시 전파한다.
 * 재시도 여부 판단을 예외 타입이 아니라 [GeminiApiException.category] 에 위임하므로
 * spring-retry 의 타입 기반 `@Retryable` 보다 분류 한 곳(GeminiApiException)에 모인다.
 *
 * [sleep] 을 주입 가능하게 둬, 재시도 횟수·분류 로직을 실제 대기 없이 단위 테스트할 수 있다.
 */
class GeminiRetry(
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun <T> execute(block: () -> T): T {
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (e: GeminiApiException) {
                if (e.category != ErrorCategory.RETRYABLE || attempt >= MAX_ATTEMPTS) throw e
                val delayMs = backoffMillis(attempt)
                log.warn(
                    "Gemini 호출 재시도 {}/{} — {}ms 후 ({})",
                    attempt,
                    MAX_ATTEMPTS - 1,
                    delayMs,
                    e.message,
                )
                sleep(delayMs)
                attempt++
            }
        }
    }

    // 지수 백오프 + full jitter: [0, min(initial * 2^(attempt-1), max)] 범위의 난수.
    // jitter 는 동시에 실패한 다수 요청이 같은 시점에 재시도하며 몰리는 thundering herd 를 막는다.
    private fun backoffMillis(attempt: Int): Long {
        val exponential = INITIAL_DELAY_MS shl (attempt - 1)
        val capped = minOf(exponential, MAX_DELAY_MS)
        return Random.nextLong(capped + 1)
    }

    companion object {
        // 첫 호출 + 재시도 2회 = 최대 3회 시도.
        private const val MAX_ATTEMPTS = 3
        private const val INITIAL_DELAY_MS = 1_000L
        private const val MAX_DELAY_MS = 8_000L
    }
}
