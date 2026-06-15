package com.depromeet.piki.admin.template

import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import com.depromeet.piki.notification.domain.NotificationType
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

// 알림 템플릿 관리 화면(#250). 목록·편집(SSR) + 라이브 미리보기(AJAX). 게이트(슬랙-세션)는 #526 — 토대 단계는
// admin.enabled 로컬에서만 노출된다. actor 신원도 세션(#526) 전까지 "운영자" 로 둔다.
@Controller
@ConditionalOnAdminEnabled
@RequestMapping("/admin/templates")
class AdminTemplateController(
    private val adminTemplateService: AdminTemplateService,
) {
    @GetMapping
    fun list(model: Model): String {
        model.addAttribute("templates", adminTemplateService.list())
        return "admin/templates"
    }

    @GetMapping("/{type}")
    fun edit(
        @PathVariable type: NotificationType,
        model: Model,
    ): String {
        // ANNOUNCEMENT 내용은 템플릿이 아니라 공지로 관리 — 직접 접근 시 공지 화면으로 보낸다.
        if (type == NotificationType.ANNOUNCEMENT) return "redirect:/admin/announcements"
        model.addAttribute("template", adminTemplateService.get(type))
        return "admin/template-edit"
    }

    @PostMapping("/{type}")
    fun update(
        @PathVariable type: NotificationType,
        @RequestParam title: String,
        @RequestParam(required = false) body: String?,
        request: HttpServletRequest,
        model: Model,
    ): String {
        val safeBody = body ?: ""
        return try {
            adminTemplateService.update(type, title, safeBody, actor = "운영자", clientIp = clientIp(request))
            "redirect:/admin/templates?updated"
        } catch (e: IllegalArgumentException) {
            // 선언 안 된 변수 등 검증 실패 — 제출값을 유지한 채 편집 화면에 에러를 표시한다(400 JSON 대신 SSR).
            model.addAttribute("template", adminTemplateService.get(type).copy(title = title, body = safeBody))
            model.addAttribute("error", e.message)
            "admin/template-edit"
        }
    }

    @PostMapping("/{type}/preview")
    @ResponseBody
    fun preview(
        @PathVariable type: NotificationType,
        @RequestParam title: String,
        @RequestParam(required = false) body: String?,
    ): TemplatePreview = adminTemplateService.preview(type, title, body ?: "")

    private fun clientIp(request: HttpServletRequest): String =
        request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()?.ifBlank { null } ?: request.remoteAddr
}
