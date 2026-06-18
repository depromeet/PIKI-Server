package com.depromeet.piki.admin.announcement

import com.depromeet.piki.admin.audit.AdminAuditLogRepository
import com.depromeet.piki.announcement.domain.Announcement
import com.depromeet.piki.announcement.repository.AnnouncementRepository
import com.depromeet.piki.support.IntegrationTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime
import kotlin.test.assertTrue

// 공지 행위자 추적(#558) — 등록·예약·예약취소가 각각 다른 audit action 으로, 행위자와 함께 admin_audit_logs 에 남는지 검증.
// admin 게이트는 IntegrationTestSupport 의 admin.local-bypass 로 우회되고, 폼 POST 는 CSRF 를 유지하므로 with(csrf()) 로 통과한다.
@Transactional
class AnnouncementAttributionIntegrationTest : IntegrationTestSupport() {
    @Autowired private lateinit var webApplicationContext: WebApplicationContext

    @Autowired private lateinit var announcementRepository: AnnouncementRepository

    @Autowired private lateinit var adminAuditLogRepository: AdminAuditLogRepository

    private fun mockMvc(): MockMvc =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    @Test
    fun `공지 등록하면 ANNOUNCEMENT_REGISTER 감사 로그가 행위자와 함께 남는다`() {
        mockMvc()
            .perform(post("/admin/announcements").param("title", "점검 안내").param("body", "내용").with(csrf()))
            .andExpect(status().is3xxRedirection)

        val logs = adminAuditLogRepository.findAll()
        assertTrue(
            logs.any { it.action == "ANNOUNCEMENT_REGISTER" && it.actor.isNotBlank() },
            "등록 시 ANNOUNCEMENT_REGISTER 가 행위자와 함께 남아야 한다. 실제=${logs.map { it.action }}",
        )
    }

    @Test
    fun `예약은 ANNOUNCEMENT_SCHEDULE, 예약취소는 ANNOUNCEMENT_SCHEDULE_CANCEL 로 구분돼 남는다`() {
        val announcement = announcementRepository.save(Announcement(title = "예약 공지", body = "내용", target = "토큰 보유자 전체"))
        val id = announcement.getId()
        val future = LocalDateTime.now(Announcement.KST).plusDays(1).toString()
        val mockMvc = mockMvc()

        mockMvc
            .perform(post("/admin/announcements/$id/send").param("scheduledAt", future).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertTrue(
            adminAuditLogRepository.findAll().any { it.action == "ANNOUNCEMENT_SCHEDULE" },
            "예약 시 ANNOUNCEMENT_SCHEDULE 가 남아야 한다(발송과 다른 코드)",
        )

        mockMvc
            .perform(post("/admin/announcements/$id/cancel").with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertTrue(
            adminAuditLogRepository.findAll().any { it.action == "ANNOUNCEMENT_SCHEDULE_CANCEL" },
            "예약취소 시 ANNOUNCEMENT_SCHEDULE_CANCEL 가 남아야 한다",
        )
    }
}
