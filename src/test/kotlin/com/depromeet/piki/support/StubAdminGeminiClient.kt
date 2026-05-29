package com.depromeet.piki.support

import com.depromeet.piki.admin.chat.gemini.AdminGeminiClient
import com.depromeet.piki.admin.chat.gemini.AdminGeminiRequest
import com.depromeet.piki.admin.chat.gemini.AdminGeminiResponse

/**
 * admin Gemini function calling 외부 호출 stub.
 *
 * 멀티턴을 표현하기 위해 handler 람다를 매 테스트가 본문에서 세팅한다(보통 ArrayDeque 를 클로저로 캡처해
 * 순서대로 응답을 돌려준다). default 는 throw 라 명시 세팅을 빠뜨리면 호출 시점에 즉시 깨진다 — 이전 테스트의
 * 상태가 잔존해 통과해버리는 함정을 차단한다.
 */
class StubAdminGeminiClient : AdminGeminiClient {
    var handler: (AdminGeminiRequest) -> AdminGeminiResponse = {
        error("StubAdminGeminiClient.handler 를 테스트 본문에서 명시 세팅해야 한다")
    }

    override fun generate(request: AdminGeminiRequest): AdminGeminiResponse = handler(request)
}
