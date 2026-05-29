package com.depromeet.piki.admin.chat

/**
 * 사용자 승인을 기다리는 write tool 호출.
 *
 * HttpSession 에 보관되며 [AdminChatSession.consumePending] 으로 actionId 일치 + TTL 내일 때만 1회 소비된다.
 */
data class PendingAction(
    val actionId: String,
    val toolName: String,
    val args: Map<String, Any?>,
    val createdAtEpochMs: Long,
)
