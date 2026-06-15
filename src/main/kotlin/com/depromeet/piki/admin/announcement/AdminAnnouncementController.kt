package com.depromeet.piki.admin.announcement

import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.time.LocalDateTime

// 공지 등록·예약/발송·결과 화면(#391/#489). 등록(초안)과 발송(목록에서 선택)을 분리해 발송 시 자유입력이 없어 오타가 안 생긴다.
// 발송은 즉시 또는 예약(시각 지정), 결과는 집계+진행률 폴링으로 본다. 게이트(슬랙-세션)·actor 신원은 #526.
@Controller
@ConditionalOnAdminEnabled
@RequestMapping("/admin/announcements")
class AdminAnnouncementController(
    private val adminAnnouncementService: AdminAnnouncementService,
) {
    @GetMapping
    fun page(model: Model): String {
        model.addAttribute("announcements", adminAnnouncementService.list())
        model.addAttribute("recipientCount", adminAnnouncementService.recipientCount())
        return "admin/announcements"
    }

    // 공지 초안 등록(DRAFT). 발송/예약은 아래 send 로만.
    @PostMapping
    fun register(
        @RequestParam title: String,
        @RequestParam(required = false) body: String?,
    ): String {
        adminAnnouncementService.register(title, body ?: "")
        return "redirect:/admin/announcements?registered"
    }

    // 발송/예약 설정 페이지 — 발송 전 "대상자 추출"(토큰 보유자 수)·내용·푸시 미리보기 + 발송 시각 선택. 실제 발송은 아래 POST.
    @GetMapping("/{id}/send")
    fun confirmSend(
        @PathVariable id: Long,
        model: Model,
    ): String {
        val announcement = adminAnnouncementService.get(id)
        if (!announcement.isDraft) return "redirect:/admin/announcements" // 발송됐거나 예약된 건 재설정 불가
        model.addAttribute("announcement", announcement)
        model.addAttribute("recipientCount", adminAnnouncementService.recipientCount())
        return "admin/announcement-send"
    }

    // 발송 확정 — scheduledAt 비우면 즉시, 채우면 그 시각으로 예약. 즉시면 결과 화면으로, 예약이면 목록으로.
    @PostMapping("/{id}/send")
    fun send(
        @PathVariable id: Long,
        @RequestParam(required = false) scheduledAt: String?,
        request: HttpServletRequest,
    ): String {
        val at = scheduledAt?.trim()?.ifBlank { null }?.let { LocalDateTime.parse(it) }
        adminAnnouncementService.schedule(id, at, actor = "운영자", clientIp = clientIp(request))
        // 예약이면 목록에서 예약 상태를 보고, 즉시면 바로 결과(진행률) 화면으로 보낸다.
        return at?.let { "redirect:/admin/announcements?scheduled" } ?: "redirect:/admin/announcements/$id/result"
    }

    // 예약 취소 — SCHEDULED → DRAFT.
    @PostMapping("/{id}/cancel")
    fun cancel(
        @PathVariable id: Long,
        request: HttpServletRequest,
    ): String {
        adminAnnouncementService.cancelSchedule(id, actor = "운영자", clientIp = clientIp(request))
        return "redirect:/admin/announcements?canceled"
    }

    // 발송 결과 화면 — 집계(성공·실패·코드별)와 진행률 %. SENDING 동안 result.json 을 폴링해 갱신한다.
    @GetMapping("/{id}/result")
    fun result(
        @PathVariable id: Long,
        model: Model,
    ): String {
        model.addAttribute("result", adminAnnouncementService.result(id))
        return "admin/announcement-result"
    }

    // 진행률·집계 폴링용 JSON(SSR 내부 엔드포인트 — 공개 API 래퍼와 무관).
    @GetMapping("/{id}/result.json")
    @ResponseBody
    fun resultJson(
        @PathVariable id: Long,
    ): AnnouncementResult = adminAnnouncementService.result(id)

    // 등록한 초안 삭제 (발송·예약된 건 불가).
    @PostMapping("/{id}/delete")
    fun delete(
        @PathVariable id: Long,
    ): String {
        adminAnnouncementService.delete(id)
        return "redirect:/admin/announcements?deleted"
    }

    private fun clientIp(request: HttpServletRequest): String =
        request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()?.ifBlank { null } ?: request.remoteAddr
}
