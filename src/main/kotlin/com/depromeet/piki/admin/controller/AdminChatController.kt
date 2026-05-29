package com.depromeet.piki.admin.controller

import com.depromeet.piki.admin.chat.AdminChatResult
import com.depromeet.piki.admin.chat.AdminChatService
import com.depromeet.piki.admin.chat.AdminChatSession
import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import com.depromeet.piki.admin.controller.dto.AdminChatRequest
import com.depromeet.piki.admin.controller.dto.AdminChatResponse
import com.depromeet.piki.admin.controller.dto.AdminConfirmRequest
import com.depromeet.piki.admin.exception.AdminChatException
import com.depromeet.piki.product.service.gemini.GeminiApiException
import io.swagger.v3.oas.annotations.Hidden
import jakarta.servlet.http.HttpSession
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * admin 챗봇 JSON 엔드포인트.
 *
 * 의도된 예외(AdminChatException·GeminiApiException)는 자체 [AdminChatResponse] 로 변환해 chat.js 가 같은
 * 형태로 처리하게 한다(전역 @RestControllerAdvice 는 JSON contract 가 달라 admin UI 와 안 맞음). 예상 못한
 * 예외는 잡지 않고 전파해 500(버그 신호)으로 남긴다. `@Hidden` 으로 OpenAPI 문서에서 제외한다.
 */
@RestController
@Hidden
@ConditionalOnAdminEnabled
class AdminChatController(
    private val adminChatService: AdminChatService,
) {
    @PostMapping("/admin/api/chat")
    fun chat(
        @Valid @RequestBody request: AdminChatRequest,
        session: HttpSession,
    ): AdminChatResponse = handle { adminChatService.chat(request.message, AdminChatSession(session)) }

    @PostMapping("/admin/api/chat/confirm")
    fun confirm(
        @Valid @RequestBody request: AdminConfirmRequest,
        session: HttpSession,
    ): AdminChatResponse = handle { adminChatService.confirm(request.actionId, AdminChatSession(session)) }

    private fun handle(block: () -> AdminChatResult): AdminChatResponse =
        try {
            AdminChatResponse.from(block())
        } catch (e: AdminChatException) {
            log.info("admin chat 처리 실패: {}", e.message)
            AdminChatResponse.error(e.message)
        } catch (e: GeminiApiException) {
            log.warn("admin chat Gemini 호출 실패: {}", e.message)
            AdminChatResponse.error("LLM 호출에 실패했습니다. 잠시 후 다시 시도해 주세요.")
        }

    companion object {
        private val log = LoggerFactory.getLogger(AdminChatController::class.java)
    }
}
