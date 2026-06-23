package com.depromeet.piki.admin.announcement

import com.depromeet.piki.announcement.domain.Announcement
import com.depromeet.piki.announcement.repository.AnnouncementRepository
import com.depromeet.piki.support.IntegrationTestSupport
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import kotlin.test.assertEquals

// 공지 작성·수정 admin 페이지(#561)가 마크다운 에디터·푸시 필드·미리보기를 포함해 깨짐 없이 렌더되는지,
// 수정(edit) 흐름이 DRAFT 내용을 갱신하는지 검증한다. Thymeleaf 에러는 부팅이 아니라 '렌더 시점'에 드러나므로
// GET 으로 실제 렌더해 회귀를 잡는다. admin 게이트는 admin.local-bypass 로 우회, 폼 POST 는 with(csrf()).
@Transactional
class AdminAnnouncementPageRenderIntegrationTest : IntegrationTestSupport() {
    @Autowired private lateinit var webApplicationContext: WebApplicationContext

    @Autowired private lateinit var announcementRepository: AnnouncementRepository

    private fun mockMvc(): MockMvc =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    @Test
    fun `공지 등록 페이지는 마크다운 에디터·푸시 필드·미리보기를 포함해 렌더된다`() {
        mockMvc()
            .perform(get("/admin/announcements"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("id=\"editor\"")))
            .andExpect(content().string(containsString("id=\"bodyField\"")))
            .andExpect(content().string(containsString("name=\"pushEnabled\"")))
            .andExpect(content().string(containsString("name=\"pushTitle\"")))
            .andExpect(content().string(containsString("name=\"pushBody\"")))
            .andExpect(content().string(containsString("id=\"pushPreview\"")))
    }

    @Test
    fun `공지 수정 페이지는 에디터와 DRAFT 의 기존 제목을 채워 렌더된다`() {
        val a = announcementRepository.save(Announcement("원래 제목", "원래 본문", "토큰 보유자 전체"))

        mockMvc()
            .perform(get("/admin/announcements/${a.getId()}/edit"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("id=\"editor\"")))
            .andExpect(content().string(containsString("원래 제목"))) // title 이 th:value 로 채워짐
    }

    @Test
    fun `발송 확인 페이지는 공지 페이지 렌더 미리보기와 푸시 미리보기를 포함해 렌더된다`() {
        // pushTitle 을 비워 effectivePushTitle 이 공지 title 로 폴백되는지도 함께 확인
        val a = announcementRepository.save(Announcement("출시 안내", "## 새 기능\n반가워요", "토큰 보유자 전체", true, "", ""))

        mockMvc()
            .perform(get("/admin/announcements/${a.getId()}/send"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("id=\"bodyViewer\""))) // 공지 페이지 마크다운 렌더 미리보기
            .andExpect(content().string(containsString("푸시 / 알림센터 미리보기")))
            .andExpect(content().string(containsString("출시 안내"))) // pushTitle 빈값 → 공지 title 로 폴백
    }

    @Test
    fun `푸시 비활성 공지의 발송 확인 페이지는 푸시 미발송 안내를 표시한다`() {
        val a = announcementRepository.save(Announcement("점검 공지", "본문", "토큰 보유자 전체", false, "", ""))

        mockMvc()
            .perform(get("/admin/announcements/${a.getId()}/send"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("푸시 미발송")))
    }

    @Test
    fun `DRAFT 공지를 수정하면 제목·본문·푸시필드가 갱신된다`() {
        val a = announcementRepository.save(Announcement("원래", "원래본문", "토큰 보유자 전체"))
        val id = a.getId()

        mockMvc()
            .perform(
                post("/admin/announcements/$id/edit")
                    .param("title", "새 제목")
                    .param("body", "## 새 본문")
                    .param("pushEnabled", "true")
                    .param("pushTitle", "푸시 제목")
                    .param("pushBody", "푸시 본문")
                    .with(csrf()),
            ).andExpect(status().is3xxRedirection)

        val updated = announcementRepository.findById(id).get()
        assertEquals("새 제목", updated.title)
        assertEquals("## 새 본문", updated.body)
        assertEquals("푸시 제목", updated.pushTitle)
        assertEquals("푸시 본문", updated.pushBody)
    }
}
