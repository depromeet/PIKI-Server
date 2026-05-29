package com.depromeet.piki.admin.chat

/**
 * AdminChatService 한 번의 처리 결과.
 *
 * - [Message]: 모델이 최종 자연어 답변을 냈다(조회 결과 요약 또는 작업 완료 보고).
 * - [Confirmation]: write tool 호출이 결정돼 사용자 승인이 필요하다. actionId 로 승인 요청한다.
 */
sealed interface AdminChatResult {
    data class Message(
        val text: String,
    ) : AdminChatResult

    data class Confirmation(
        val summary: String,
        val actionId: String,
    ) : AdminChatResult
}
