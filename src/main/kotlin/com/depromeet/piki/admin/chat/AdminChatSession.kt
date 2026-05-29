package com.depromeet.piki.admin.chat

import com.depromeet.piki.admin.chat.gemini.GeminiContent
import jakarta.servlet.http.HttpSession
import java.util.UUID

/**
 * HttpSession 위에 admin 대화 상태(멀티턴 히스토리 + 승인 대기 작업)를 타입세이프하게 얹는 래퍼.
 *
 * 빈이 아니라 매 요청 컨트롤러가 HttpSession 으로 생성한다. form login 으로 이미 STATEFUL 세션이 있으므로
 * 단일 관리자에겐 세션 보관이 가장 단순하다(멀티 인스턴스 대비 Spring Session Redis 전환은 후속).
 */
class AdminChatSession(
    private val httpSession: HttpSession,
) {
    @Suppress("UNCHECKED_CAST")
    fun history(): List<GeminiContent> = (httpSession.getAttribute(HISTORY_KEY) as? List<GeminiContent>) ?: emptyList()

    fun saveHistory(contents: List<GeminiContent>) {
        httpSession.setAttribute(HISTORY_KEY, contents)
    }

    fun clear() {
        httpSession.removeAttribute(HISTORY_KEY)
        httpSession.removeAttribute(PENDING_KEY)
    }

    fun savePending(
        toolName: String,
        args: Map<String, Any?>,
    ): String {
        val actionId = UUID.randomUUID().toString()
        httpSession.setAttribute(PENDING_KEY, PendingAction(actionId, toolName, args, System.currentTimeMillis()))
        return actionId
    }

    /** actionId 가 일치하고 TTL 내일 때만 반환한다. 조회 즉시 세션에서 제거해 1회성으로 만든다(이중 실행 방지). */
    fun consumePending(actionId: String): PendingAction? {
        val pending = httpSession.getAttribute(PENDING_KEY) as? PendingAction ?: return null
        httpSession.removeAttribute(PENDING_KEY)
        val expired = System.currentTimeMillis() - pending.createdAtEpochMs > PENDING_TTL_MS
        return if (pending.actionId == actionId && !expired) pending else null
    }

    companion object {
        private const val HISTORY_KEY = "ADMIN_CHAT_HISTORY"
        private const val PENDING_KEY = "ADMIN_CHAT_PENDING"
        private const val PENDING_TTL_MS = 5 * 60 * 1000L
    }
}
