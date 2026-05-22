package com.depromeet.team3.product.service.gemini

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gemini")
data class GeminiProperties(
    val apiKey: String,
    val model: String = DEFAULT_MODEL,
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
        // max-attempts 는 총 시도 횟수(초기 호출 + 재시도). 기본 2 = 초기 호출 1회 + 재시도 1회.
        // 한 요청이 유발하는 billed API 호출 횟수 상한이므로 비용·quota 에 맞춰 운영에서 조정한다.
        val maxAttempts: Int = 2,
        val initialDelayMs: Long = 1_000,
    ) {
        init {
            require(maxAttempts >= 1) { "gemini.retry.max-attempts 는 1 이상이어야 합니다: $maxAttempts" }
            require(initialDelayMs >= 0) { "gemini.retry.initial-delay-ms 는 0 이상이어야 합니다: $initialDelayMs" }
        }
    }

    companion object {
        /**
         * 기본 모델의 단일 진실 원천(single source of truth).
         *
         * application.yml 등 다른 곳에 모델 리터럴을 중복 정의하지 않는다 — 여러 곳에 흩어지면
         * 한쪽만 바뀌어 "다른 모델이 기본값이 되는" drift 가 생긴다. 운영에서 모델을 바꾸려면
         * GEMINI_MODEL 환경변수로 override 한다 (relaxed binding: GEMINI_MODEL -> gemini.model).
         *
         * preview 모델은 2 주 사전공지 후 deprecate 정책이라 운영 안정성을 위해 GA 모델만 기본값으로 둔다.
         */
        const val DEFAULT_MODEL = "gemini-3.1-flash-lite"
    }
}
