package com.depromeet.piki.admin.controller.dto

import com.depromeet.piki.admin.chat.AdminChatResult

/**
 * admin 챗봇 JSON 응답 (자체 DTO — ApiResponseBody 미사용).
 *
 * type 으로 클라이언트(chat.js)가 분기한다: MESSAGE(최종 답변) / CONFIRMATION(승인 카드) / ERROR.
 */
data class AdminChatResponse(
    val type: String,
    val text: String? = null,
    val summary: String? = null,
    val actionId: String? = null,
    val error: String? = null,
) {
    companion object {
        fun message(text: String): AdminChatResponse = AdminChatResponse(type = "MESSAGE", text = text)

        fun confirmation(
            summary: String,
            actionId: String,
        ): AdminChatResponse = AdminChatResponse(type = "CONFIRMATION", summary = summary, actionId = actionId)

        fun error(reason: String): AdminChatResponse = AdminChatResponse(type = "ERROR", error = reason)

        fun from(result: AdminChatResult): AdminChatResponse =
            when (result) {
                is AdminChatResult.Message -> message(result.text)
                is AdminChatResult.Confirmation -> confirmation(result.summary, result.actionId)
            }
    }
}
