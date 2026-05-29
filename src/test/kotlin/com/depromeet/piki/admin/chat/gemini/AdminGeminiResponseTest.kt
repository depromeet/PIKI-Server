package com.depromeet.piki.admin.chat.gemini

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminGeminiResponseTest {
    @Test
    fun `functionCall part 를 추출하고 text 는 null 이다`() {
        val response =
            responseOf(
                AdminGeminiResponse.ResponsePart(functionCall = FunctionCall("list_recent_items", mapOf("limit" to 5))),
            )

        val calls = response.functionCalls()

        assertEquals(1, calls.size)
        assertEquals("list_recent_items", calls.first().name)
        assertEquals(5, (calls.first().args["limit"] as Number).toInt())
        assertNull(response.text())
    }

    @Test
    fun `text part 를 추출하고 functionCalls 는 비어 있다`() {
        val response = responseOf(AdminGeminiResponse.ResponsePart(text = "안녕하세요"))

        assertEquals("안녕하세요", response.text())
        assertTrue(response.functionCalls().isEmpty())
    }

    @Test
    fun `candidates 가 비면 functionCalls 는 비고 text 는 null 이다`() {
        val response = AdminGeminiResponse()

        assertTrue(response.functionCalls().isEmpty())
        assertNull(response.text())
    }

    @Test
    fun `toModelContent 는 빈 part 를 제외하고 model role 로 변환한다`() {
        val response =
            responseOf(
                AdminGeminiResponse.ResponsePart(functionCall = FunctionCall("t", emptyMap())),
                AdminGeminiResponse.ResponsePart(),
            )

        val content = response.toModelContent()

        assertEquals("model", content.role)
        assertEquals(1, content.parts.size)
        val firstCall = content.parts.first().functionCall
        assertEquals("t", firstCall?.name)
    }

    private fun responseOf(vararg parts: AdminGeminiResponse.ResponsePart): AdminGeminiResponse =
        AdminGeminiResponse(
            candidates =
                listOf(
                    AdminGeminiResponse.Candidate(
                        content = AdminGeminiResponse.ResponseContent(role = "model", parts = parts.toList()),
                    ),
                ),
        )
}
