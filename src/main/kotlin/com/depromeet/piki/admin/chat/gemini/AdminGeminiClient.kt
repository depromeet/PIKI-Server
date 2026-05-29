package com.depromeet.piki.admin.chat.gemini

/**
 * admin 챗봇용 Gemini function calling 호출 경계.
 *
 * 외부 호출(LLM)이므로 인터페이스로 분리해 통합테스트에서 프로그래머블 stub 으로 교체한다
 * (CLAUDE.md 모킹 정책: 외부 경계만 stub). 운영 구현은 [HttpAdminGeminiClient].
 */
interface AdminGeminiClient {
    fun generate(request: AdminGeminiRequest): AdminGeminiResponse
}
