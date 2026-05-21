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
 * 재시도 횟수·백오프는 [GeminiProperties.Retry] 로 외부 주입한다 — maxAttempts 가 곧
 * billed API 호출 상한이라 운영에서 비용·quota 에 맞춰 조정할 수 있어야 하기 때문이다.
 */
class GeminiRetry(
    private val config: GeminiProperties.Retry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun <T> execute(block: () -> T): T {
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (e: GeminiApiException) {
                if (e.category != ErrorCategory.RETRYABLE || attempt >= config.maxAttempts) throw e
                val delayMs = backoffMillis(attempt)
                log.warn(
                    "Gemini 호출 재시도 {}/{} — {}ms 후 ({})",
                    attempt,
                    config.maxAttempts - 1,
                    delayMs,
                    e.message,
                )
                Thread.sleep(delayMs)
                attempt++
            }
        }
    }

    // 지수 백오프 + full jitter: [0, initial * 2^shift] 범위의 난수.
    // jitter 는 동시에 실패한 다수 요청이 같은 시점에 재시도하며 몰리는 thundering herd 를 막는다.
    //
    // shift 는 MAX_SHIFT 로 제한 — 두 가지 목적의 안전망:
    //   1. shl 결과가 Long 부호 비트를 넘어 음수가 되면 Random.nextLong 이 IllegalArgumentException
    //      으로 깨진다. 운영자가 max-attempts 를 비현실적으로 크게 설정해도 산술적으로 안전.
    //   2. 깊은 attempts 에서 base 가 분/시간 단위로 폭주하는 것을 방지.
    // (운영자 튜닝용 max-delay-ms 와는 별개. 그건 의도적 cap, 이건 산술/폭주 안전망.)
    private fun backoffMillis(attempt: Int): Long {
        val shift = (attempt - 1).coerceAtMost(MAX_SHIFT)
        val exponential = config.initialDelayMs shl shift
        return Random.nextLong(exponential + 1)
    }

    companion object {
        // 2^5 = 32. initialDelayMs=1000 기본일 때 최대 base ≈ 32 초.
        private const val MAX_SHIFT = 5
    }
}
