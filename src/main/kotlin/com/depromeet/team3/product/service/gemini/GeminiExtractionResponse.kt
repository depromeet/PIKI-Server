package com.depromeet.team3.product.service.gemini

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiExtractionResponse(
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

    // url_context tool 결과의 fetch 상태를 관측용으로 노출한다. MVP 단계에선 로깅 용도.
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
