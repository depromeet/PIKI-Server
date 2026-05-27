package com.depromeet.piki.image.service.gemini

import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeminiImageRequestTest {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @Test
    fun `forImageAnalysis 요청은 thinkingLevel 을 minimal 로 명시한다`() {
        // 명시하지 않으면 gemini-3.1-flash-lite 에 dynamic thinking 이 붙어 응답이 ~20s 까지 튄다.
        val json =
            objectMapper.writeValueAsString(
                GeminiImageRequest.forImageAnalysis(base64Image = "AAAA", mimeType = "image/png"),
            )

        assertTrue(
            json.contains("\"thinkingConfig\":{\"thinkingLevel\":\"minimal\"}"),
            "thinkingLevel minimal 이 직렬화되지 않았다: $json",
        )
    }

    @Test
    fun `요청에 null 값 필드는 직렬화되지 않는다`() {
        // Gemini JSON Schema 파서는 STRING 타입에 붙은 "items":null 같은 잉여 null 을 스키마 위반으로 취급한다.
        val json =
            objectMapper.writeValueAsString(
                GeminiImageRequest.forImageAnalysis(base64Image = "AAAA", mimeType = "image/png"),
            )

        assertFalse(
            json.contains(":null"),
            "NON_NULL 정책인데 null 값이 직렬화됐다: $json",
        )
    }

    @Test
    fun `nullable 이 명시된 스키마 필드는 직렬화에 포함된다`() {
        // null 값 생략(NON_NULL)과 별개로, 의도적으로 박은 nullable=true 는 보존되어야 한다.
        val json =
            objectMapper.writeValueAsString(
                GeminiImageRequest.forImageAnalysis(base64Image = "AAAA", mimeType = "image/png"),
            )

        assertTrue(
            json.contains("\"nullable\":true"),
            "스키마의 nullable=true 가 누락됐다: $json",
        )
    }

    @Test
    fun `이미지 part 는 inlineData 의 mimeType·data 를 그대로 싣는다`() {
        val json =
            objectMapper.writeValueAsString(
                GeminiImageRequest.forImageAnalysis(base64Image = "BASE64DATA", mimeType = "image/jpeg"),
            )

        assertTrue(
            json.contains("\"inlineData\":{\"mimeType\":\"image/jpeg\",\"data\":\"BASE64DATA\"}"),
            "inlineData 적재가 어긋났다: $json",
        )
    }
}
