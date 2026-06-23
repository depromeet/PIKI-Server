package com.depromeet.piki.admin.announcement

import com.depromeet.piki.support.IntegrationTestSupport
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// 공지 등록 admin 페이지(#561)가 마크다운 에디터·푸시 필드·미리보기를 포함해 깨짐 없이 렌더되는지 검증한다.
// Thymeleaf 템플릿 에러는 부팅이 아니라 '렌더 시점'에 드러나므로, GET 으로 실제 렌더해 회귀를 잡는다.
// (admin 게이트는 IntegrationTestSupport 의 admin.local-bypass 로 우회된다.)
class AdminAnnouncementPageRenderIntegrationTest : IntegrationTestSupport() {
    @Autowired private lateinit var webApplicationContext: WebApplicationContext

    @Test
    fun `공지 등록 페이지는 마크다운 에디터·푸시 필드·미리보기를 포함해 렌더된다`() {
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()
            .perform(get("/admin/announcements"))
            .andExpect(status().isOk)
            // 마크다운 에디터 마운트 지점 + 제출용 hidden body
            .andExpect(content().string(containsString("id=\"editor\"")))
            .andExpect(content().string(containsString("id=\"bodyField\"")))
            // 푸시 전용 입력(토글·제목·본문)
            .andExpect(content().string(containsString("name=\"pushEnabled\"")))
            .andExpect(content().string(containsString("name=\"pushTitle\"")))
            .andExpect(content().string(containsString("name=\"pushBody\"")))
            // 푸시 미리보기 카드
            .andExpect(content().string(containsString("id=\"pushPreview\"")))
    }
}
