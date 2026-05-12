package com.depromeet.team3.product.service.gemini

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeminiExtractionResponseTest {
    @Test
    fun `candidates 가 비어 있으면 noTextPart 예외를 던진다`() {
        val response = GeminiExtractionResponse(candidates = emptyList())

        assertFailsWith<GeminiApiException> {
            response.extractText()
        }
    }

    @Test
    fun `parts 가 비어 있으면 noTextPart 예외를 던진다`() {
        val response =
            GeminiExtractionResponse(
                candidates =
                    listOf(
                        GeminiExtractionResponse.Candidate(
                            content = GeminiExtractionResponse.Content(parts = emptyList()),
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
            GeminiExtractionResponse(
                candidates =
                    listOf(
                        GeminiExtractionResponse.Candidate(
                            content =
                                GeminiExtractionResponse.Content(
                                    parts =
                                        listOf(
                                            GeminiExtractionResponse.Part(text = """{"isProductPage":true}"""),
                                        ),
                                ),
                        ),
                    ),
            )

        assertEquals("""{"isProductPage":true}""", response.extractText())
    }
}
