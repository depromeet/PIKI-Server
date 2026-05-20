package com.depromeet.team3.product.service.gemini

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Gemini generateContent 응답 wire 모델.
 *
 * Gemini API 는 범용적으로 설계되어 응답이 항상 중첩 리스트 구조로 옴 — `candidates → content → parts`.
 * 이 프로젝트에서는 후보 1개·텍스트 파트 1개만 사용하므로 [extractText] 가 `firstOrNull` 로 바로 꺼낸다.
 *
 * 두 추출기(`GeminiProductExtractor`, `GeminiOcrExtractor`) 가 같은 wire 모델을 공유한다.
 * `urlContextMetadata` 는 url_context 도구를 쓰는 HTML 추출 흐름에서만 채워지고 OCR 흐름에선 null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiGenerateContentResponse(
    val candidates: List<Candidate>,
) {
    fun extractText(): String =
        candidates
            .firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull()
            ?.text
            ?: throw GeminiApiException.noTextPart()

    // url_context tool 결과의 fetch 상태를 관측용으로 노출. MVP 단계에선 로깅 용도.
    fun urlContextMetadata(): UrlContextMetadata? = candidates.firstOrNull()?.urlContextMetadata

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Candidate(
        val content: Content,
        val urlContextMetadata: UrlContextMetadata? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Content(
        val parts: List<Part>,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Part(
        val text: String,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class UrlContextMetadata(
        val urlMetadata: List<UrlMetadata> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class UrlMetadata(
        val retrievedUrl: String? = null,
        val urlRetrievalStatus: String? = null,
    )
}
