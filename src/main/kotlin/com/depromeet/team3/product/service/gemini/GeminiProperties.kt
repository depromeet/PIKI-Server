package com.depromeet.team3.product.service.gemini

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gemini")
data class GeminiProperties(
    val apiKey: String,
    val model: String = "gemini-2.5-flash",
    val retry: Retry = Retry(),
) {
    init {
        require(apiKey.isNotBlank()) { "GEMINI_API_KEY 가 비어 있습니다." }
        require(model.isNotBlank()) { "gemini.model 이 비어 있습니다." }
    }

    // data class 기본 toString 은 apiKey 를 그대로 노출하므로, 로그 유출 방지를 위해 마스킹한다.
    override fun toString(): String = "GeminiProperties(apiKey=*secret*, model=$model, retry=$retry)"

    /**
     * Gemini 호출 재시도 파라미터. maxAttempts 는 곧 한 요청이 유발할 수 있는
     * billed API 호출 횟수 상한이므로, API 비용·quota 를 고려해 운영에서 직접 조정한다.
     */
    data class Retry(
        val maxAttempts: Int = 3,
        val initialDelayMs: Long = 1_000,
        val maxDelayMs: Long = 8_000,
    ) {
        init {
            require(maxAttempts >= 1) { "gemini.retry.max-attempts 는 1 이상이어야 합니다: $maxAttempts" }
            require(initialDelayMs >= 0) { "gemini.retry.initial-delay-ms 는 0 이상이어야 합니다: $initialDelayMs" }
            require(maxDelayMs >= initialDelayMs) {
                "gemini.retry.max-delay-ms($maxDelayMs) 는 initial-delay-ms($initialDelayMs) 이상이어야 합니다."
            }
        }
    }
}
