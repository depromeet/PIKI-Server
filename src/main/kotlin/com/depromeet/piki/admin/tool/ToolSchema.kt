package com.depromeet.piki.admin.tool

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Gemini `functionDeclarations[].parameters` (OpenAPI subset JSON schema) 직렬화 모델.
 *
 * Gemini schema 파서는 `"properties": null` 같은 잉여 null 필드를 위반으로 취급하므로 null 필드를 전부
 * 생략한다 (`GeminiExtractionRequest.JsonSchema` 와 같은 정책). tool 정의가 schema 를 한 곳에서 선언하도록
 * 자주 쓰는 형태를 팩토리로 제공한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ToolSchema(
    val type: String,
    val description: String? = null,
    val properties: Map<String, ToolSchema>? = null,
    val items: ToolSchema? = null,
    val required: List<String>? = null,
    val nullable: Boolean? = null,
) {
    companion object {
        const val TYPE_OBJECT = "object"
        const val TYPE_STRING = "string"
        const val TYPE_INTEGER = "integer"
        const val TYPE_NUMBER = "number"
        const val TYPE_BOOLEAN = "boolean"
        const val TYPE_ARRAY = "array"

        fun obj(
            properties: Map<String, ToolSchema>,
            required: List<String> = emptyList(),
            description: String? = null,
        ): ToolSchema =
            ToolSchema(
                type = TYPE_OBJECT,
                description = description,
                properties = properties,
                required = required.ifEmpty { null },
            )

        fun string(description: String? = null): ToolSchema = ToolSchema(type = TYPE_STRING, description = description)

        fun integer(description: String? = null): ToolSchema =
            ToolSchema(type = TYPE_INTEGER, description = description)
    }
}
