package com.depromeet.piki.admin.feature

import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import io.swagger.v3.oas.annotations.Hidden
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.security.Principal

/**
 * 백오피스 기능 요청 인박스 페이지.
 *
 * 모인 요청을 보여주고, 한 줄 제목으로 새 요청을 남기거나 처리 상태를 토글한다. 등록·토글은 PRG(POST-Redirect-Get)
 * 패턴 — 성공/실패를 flash 로 전달해 새로고침 중복 제출을 막는다. `@Hidden` 으로 OpenAPI 문서에서 제외한다.
 */
@Controller
@Hidden
@ConditionalOnAdminEnabled
class AdminFeatureRequestController(
    private val service: AdminFeatureRequestService,
) {
    @GetMapping("/admin/feature-requests")
    fun list(model: Model): String {
        model.addAttribute("requests", service.recent())
        return "admin/feature-requests"
    }

    @PostMapping("/admin/feature-requests")
    fun create(
        @RequestParam title: String,
        principal: Principal,
        redirectAttributes: RedirectAttributes,
    ): String {
        try {
            service.create(title, principal.name)
            redirectAttributes.addFlashAttribute("message", "기능 요청을 등록했습니다.")
        } catch (e: IllegalArgumentException) {
            // 폼 입력 검증 실패 — 사용자에게 사유를 보여주고 폼으로 돌려보낸다.
            log.info("admin 기능 요청 등록 입력 오류: {}", e.message)
            redirectAttributes.addFlashAttribute("error", e.message)
        }
        return "redirect:/admin/feature-requests"
    }

    @PostMapping("/admin/feature-requests/{id}/status")
    fun toggleStatus(
        @PathVariable id: Long,
        principal: Principal,
        redirectAttributes: RedirectAttributes,
    ): String {
        try {
            service.toggleStatus(id, principal.name)
            redirectAttributes.addFlashAttribute("message", "처리 상태를 변경했습니다.")
        } catch (e: IllegalArgumentException) {
            log.info("admin 기능 요청 상태 변경 오류: {}", e.message)
            redirectAttributes.addFlashAttribute("error", e.message)
        }
        return "redirect:/admin/feature-requests"
    }

    companion object {
        private val log = LoggerFactory.getLogger(AdminFeatureRequestController::class.java)
    }
}
