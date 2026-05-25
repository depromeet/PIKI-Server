package com.depromeet.piki.product.service.gemini

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeminiGenerateContentResponseTest {
    @Test
    fun `candidates 가 비어 있으면 noTextPart 예외를 던진다`() {
        val response = GeminiGenerateContentResponse(candidates = emptyList())

        assertFailsWith<GeminiApiException> {
            response.extractText()
        }
    }

    @Test
    fun `parts 가 비어 있으면 noTextPart 예외를 던진다`() {
        val response =
            GeminiGenerateContentResponse(
                candidates =
                    listOf(
                        GeminiGenerateContentResponse.Candidate(
                            content = GeminiGenerateContentResponse.Content(parts = emptyList()),
                        ),
                    ),
            )

        assertFailsWith<GeminiApiException> {
            response.extractText()
        }
    }

    @Test
    fun `정상 응답은 첫번째 candidate 의 첫번째 part text 를 반환한다`() {
        val response =
            GeminiGenerateContentResponse(
                candidates =
                    listOf(
                        GeminiGenerateContentResponse.Candidate(
                            content =
                                GeminiGenerateContentResponse.Content(
                                    parts =
                                        listOf(
                                            GeminiGenerateContentResponse.Part(text = """{"isProductPage":true}"""),
                                        ),
                                ),
                        ),
                    ),
            )

        assertEquals("""{"isProductPage":true}""", response.extractText())
    }
}
