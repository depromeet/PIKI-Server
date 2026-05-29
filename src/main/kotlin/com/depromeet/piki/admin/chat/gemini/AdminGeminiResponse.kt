package com.depromeet.piki.admin.chat.gemini

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * admin 챗봇 전용 Gemini generateContent 응답.
 *
 * 기존 `GeminiGenerateContentResponse` 는 `parts[0].text` 만 파싱해 function calling 응답(functionCall part)을
 * 못 받는다. 한 턴에 여러 functionCall(병렬 호출)이 올 수 있어 [functionCalls] 를 리스트로 노출한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AdminGeminiResponse(
    val candidates: List<Candidate> = emptyList(),
) {
    fun functionCalls(): List<FunctionCall> = firstParts().mapNotNull { it.functionCall }

    fun text(): String? =
        firstParts()
            .mapNotNull { it.text }
            .joinToString("\n")
            .ifBlank { null }

    /**
     * 모델 응답을 멀티턴 히스토리에 그대로 누적하기 위해 요청 [GeminiContent] 타입으로 변환한다.
     * text·functionCall 이 모두 없는 빈 part 는 제외해 다음 요청에 빈 객체가 섞이지 않게 한다.
     */
    fun toModelContent(): GeminiContent =
        GeminiContent(
            role = "model",
            parts =
                firstParts()
                    .filter { listOfNotNull(it.text, it.functionCall).isNotEmpty() }
                    .map { GeminiPart(text = it.text, functionCall = it.functionCall) },
        )

    private fun firstParts(): List<ResponsePart> = candidates.firstOrNull()?.content?.parts ?: emptyList()

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Candidate(
        val content: ResponseContent = ResponseContent(),
        val finishReason: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ResponseContent(
        val role: String? = null,
        val parts: List<ResponsePart> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ResponsePart(
        val text: String? = null,
        val functionCall: FunctionCall? = null,
    )
}
