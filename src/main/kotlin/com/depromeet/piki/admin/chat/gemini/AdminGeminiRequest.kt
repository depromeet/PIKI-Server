package com.depromeet.piki.admin.chat.gemini

import com.depromeet.piki.admin.tool.ToolSchema
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

/**
 * admin 챗봇 전용 Gemini generateContent 요청 (function calling).
 *
 * 기존 추출용 DTO(`GeminiExtractionRequest`/`GeminiImageRequest`)는 structured-output 전용이라 tools·
 * functionCall·functionResponse part 를 표현할 수 없다. 회귀 위험을 피하려 admin 전용으로 새로 둔다.
 * Gemini schema 파서가 잉여 null 을 위반으로 취급하므로 모든 part 타입은 nullable 필드 + NON_NULL 직렬화로
 * "해당하는 한 종류"만 전송한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AdminGeminiRequest(
    val contents: List<GeminiContent>,
    val tools: List<GeminiTool>,
    val systemInstruction: GeminiContent? = null,
    val toolConfig: ToolConfig? = null,
) {
    data class ToolConfig(
        val functionCallingConfig: FunctionCallingConfig,
    )

    data class FunctionCallingConfig(
        // AUTO: 모델이 도구 호출 여부를 스스로 판단. ANY(강제)·NONE(무력화)은 admin 챗봇에 부적합.
        val mode: String = "AUTO",
    )
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GeminiContent(
    val role: String,
    val parts: List<GeminiPart>,
)

// 한 part 는 text · functionCall · functionResponse 중 하나. NON_NULL 로 나머지는 직렬화에서 생략된다.
@JsonInclude(JsonInclude.Include.NON_NULL)
data class GeminiPart(
    val text: String? = null,
    val functionCall: FunctionCall? = null,
    val functionResponse: FunctionResponse? = null,
)

// 요청(모델 응답을 히스토리에 누적)·응답(모델이 호출한 함수) 양쪽에서 공유한다.
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class FunctionCall(
    val name: String = "",
    val args: Map<String, Any?> = emptyMap(),
)

// functionResponse.response 는 반드시 JSON object 여야 한다 — 리스트/스칼라는 {value:...} 로 감싼다.
@JsonInclude(JsonInclude.Include.NON_NULL)
data class FunctionResponse(
    val name: String,
    val response: Map<String, Any?>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GeminiTool(
    val functionDeclarations: List<FunctionDeclaration>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: ToolSchema,
)
