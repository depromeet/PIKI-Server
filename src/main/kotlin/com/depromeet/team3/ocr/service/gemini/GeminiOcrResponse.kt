package com.depromeet.team3.ocr.service.gemini

import com.depromeet.team3.product.service.gemini.GeminiApiException

/**
 * Gemini API의 generateContent 응답을 매핑하는 클래스.
 *
 * Gemini API는 범용적으로 설계되어 있어 응답이 항상 아래와 같은 중첩 리스트 구조로 옴:
 * - candidates: 같은 요청에 대해 여러 답변 후보를 생성할 수 있음 (candidateCount 파라미터로 제어, 기본값 1)
 * - parts: 하나의 답변이 텍스트, 이미지 등 여러 파트로 구성될 수 있음
 *
 * 이 프로젝트에서는 후보 1개, 텍스트 파트 1개만 사용하므로 extractText()에서 firstOrNull()로 바로 꺼냄.
 */
data class GeminiOcrResponse(
    val candidates: List<Candidate>,
) {
    fun extractText(): String =
        candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw GeminiApiException.noTextPart()

    data class Candidate(
        val content: Content,
    )

    data class Content(
        val parts: List<Part>,
    )

    data class Part(
        val text: String,
    )
}
