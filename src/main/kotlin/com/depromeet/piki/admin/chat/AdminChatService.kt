package com.depromeet.piki.admin.chat

import com.depromeet.piki.admin.audit.AdminAuditService
import com.depromeet.piki.admin.chat.gemini.AdminGeminiClient
import com.depromeet.piki.admin.chat.gemini.AdminGeminiRequest
import com.depromeet.piki.admin.chat.gemini.FunctionDeclaration
import com.depromeet.piki.admin.chat.gemini.FunctionResponse
import com.depromeet.piki.admin.chat.gemini.GeminiContent
import com.depromeet.piki.admin.chat.gemini.GeminiPart
import com.depromeet.piki.admin.chat.gemini.GeminiTool
import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import com.depromeet.piki.admin.exception.AdminChatException
import com.depromeet.piki.admin.tool.AdminToolRegistry
import com.depromeet.piki.admin.tool.ToolResult
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

/**
 * admin 챗봇 멀티턴 루프 오케스트레이터.
 *
 * 트랜잭션을 두지 않는다 — 외부 LLM 호출이 포함되므로(CLAUDE.md). 영속화는 tool.commit / AdminAuditService 가
 * 각자 짧은 트랜잭션으로 처리한다. read tool 은 즉시 실행하고, write tool 은 PendingConfirmation 으로 루프를
 * 중단해 사용자 승인([confirm])을 기다린다.
 */
@Service
@ConditionalOnAdminEnabled
class AdminChatService(
    private val adminGeminiClient: AdminGeminiClient,
    private val toolRegistry: AdminToolRegistry,
    private val auditService: AdminAuditService,
) {
    fun chat(
        message: String,
        session: AdminChatSession,
    ): AdminChatResult {
        val contents = session.history().toMutableList()
        contents += userContent(message)
        return runLoop(contents, session)
    }

    fun confirm(
        actionId: String,
        session: AdminChatSession,
    ): AdminChatResult {
        val pending = session.consumePending(actionId) ?: throw AdminChatException.pendingExpired()
        val tool = toolRegistry.find(pending.toolName)
        // 승인된 write 의 실제 실행. tool.commit 이 짧은 @Transactional 로 영속화한다.
        val result = tool.commit(pending.args)
        auditService.record(
            adminUsername = currentAdmin(),
            actionType = ACTION_WRITE_COMMIT,
            toolName = pending.toolName,
            parameters = pending.args,
            resultStatus = STATUS_SUCCESS,
            resultSummary = result.toString().take(RESULT_SUMMARY_MAX),
            requestMessage = null,
        )
        val contents = session.history().toMutableList()
        contents += functionResponseContent(pending.toolName, result)
        return runLoop(contents, session)
    }

    private fun runLoop(
        contents: MutableList<GeminiContent>,
        session: AdminChatSession,
    ): AdminChatResult {
        val deadline = System.currentTimeMillis() + TOTAL_BUDGET_MS
        var turn = 0
        while (turn < MAX_TURNS) {
            turn++
            if (System.currentTimeMillis() > deadline) throw AdminChatException.budgetExceeded()

            val response = adminGeminiClient.generate(buildRequest(contents))
            val calls = response.functionCalls()
            if (calls.isEmpty()) {
                val text = response.text() ?: throw AdminChatException.emptyResponse()
                contents += GeminiContent(ROLE_MODEL, listOf(GeminiPart(text = text)))
                session.saveHistory(contents)
                return AdminChatResult.Message(text)
            }

            // 첫 단계에선 단일 호출만 처리한다 — model content 와 functionResponse 의 1:1 매칭을 보장해
            // "응답 없는 functionCall" 로 Gemini 가 거부하는 상황을 막는다. 복수 호출 순차 처리는 후속 확장.
            val call = calls.first()
            if (calls.size > 1) log.warn("admin chat: 복수 functionCall {}개 중 첫 개({})만 처리한다", calls.size, call.name)
            contents += GeminiContent(ROLE_MODEL, listOf(GeminiPart(functionCall = call)))

            val tool = toolRegistry.find(call.name)
            if (tool.isWrite) {
                when (val r = tool.execute(call.args)) {
                    is ToolResult.PendingConfirmation -> {
                        session.saveHistory(contents)
                        val actionId = session.savePending(tool.name, r.args)
                        return AdminChatResult.Confirmation(r.summary, actionId)
                    }
                    // 검증 실패는 모델에 돌려 자가수정 1턴을 유도한다.
                    is ToolResult.Failed -> contents += functionResponseContent(call.name, mapOf("error" to r.reason))
                    is ToolResult.Executed -> error("write tool 이 즉시 Executed 를 반환할 수 없다: ${tool.name}")
                }
            } else {
                val payload =
                    when (val r = tool.execute(call.args)) {
                        is ToolResult.Executed -> r.payload
                        is ToolResult.Failed -> mapOf("error" to r.reason)
                        is ToolResult.PendingConfirmation ->
                            error(
                                "read tool 이 PendingConfirmation 을 반환할 수 없다: ${tool.name}",
                            )
                    }
                contents += functionResponseContent(call.name, payload)
            }
        }
        throw AdminChatException.tooManyTurns()
    }

    private fun buildRequest(contents: List<GeminiContent>): AdminGeminiRequest =
        AdminGeminiRequest(
            contents = contents,
            tools =
                listOf(
                    GeminiTool(
                        toolRegistry.all().map {
                            FunctionDeclaration(
                                name = it.name,
                                description = it.description,
                                parameters = it.parameters,
                            )
                        },
                    ),
                ),
            systemInstruction = GeminiContent(ROLE_USER, listOf(GeminiPart(text = SYSTEM_PROMPT))),
            toolConfig = AdminGeminiRequest.ToolConfig(AdminGeminiRequest.FunctionCallingConfig()),
        )

    private fun userContent(message: String): GeminiContent =
        GeminiContent(ROLE_USER, listOf(GeminiPart(text = message)))

    // Gemini 는 functionResponse 를 user turn 으로 받는다(도구 결과를 사용자가 제공하는 것으로 모델링).
    private fun functionResponseContent(
        name: String,
        payload: Map<String, Any?>,
    ): GeminiContent = GeminiContent(ROLE_USER, listOf(GeminiPart(functionResponse = FunctionResponse(name, payload))))

    private fun currentAdmin(): String = SecurityContextHolder.getContext().authentication?.name ?: "unknown"

    companion object {
        private val log = LoggerFactory.getLogger(AdminChatService::class.java)

        private const val ROLE_USER = "user"
        private const val ROLE_MODEL = "model"
        private const val ACTION_WRITE_COMMIT = "WRITE_COMMIT"
        private const val STATUS_SUCCESS = "SUCCESS"
        private const val RESULT_SUMMARY_MAX = 500

        // 무한 루프 가드: 모델↔도구 왕복 턴 수 상한.
        private const val MAX_TURNS = 5

        // 누적 시간 가드: read tool DB 시간 + LLM latency 합산. 단발 read-timeout(30s)과 별개.
        private const val TOTAL_BUDGET_MS = 25_000L

        private val SYSTEM_PROMPT =
            """
            당신은 PIKI 개발 서버의 데이터베이스 운영을 돕는 관리자 어시스턴트입니다.
            관리자의 자연어 요청을 이해하고, 제공된 도구(function)를 사용해 처리합니다.

            규칙:
            - 데이터 조회·변경은 반드시 제공된 도구를 통해서만 수행합니다. 임의의 SQL 을 만들거나 결과를 가정하지 마세요.
            - 도구로 처리할 수 없는 요청은 솔직히 할 수 없다고 답합니다.
            - 변경(추가·수정·삭제) 작업은 시스템이 관리자 확인을 받은 뒤 실행합니다. 당신은 적절한 도구를 호출하기만 하면 됩니다.
            - 도구 실행 결과를 받으면 그 내용을 관리자가 이해하기 쉽게 요약해 전달합니다.
            - 모든 답변은 한국어로, 간결하고 명확하게 작성합니다.
            """.trimIndent()
    }
}
