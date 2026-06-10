package com.depromeet.piki.admin.feature

import com.depromeet.piki.admin.audit.AdminAuditLogRepository
import com.depromeet.piki.support.IntegrationTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import kotlin.test.assertEquals

@Transactional
class AdminFeatureRequestControllerIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var repository: AdminFeatureRequestRepository

    @Autowired
    private lateinit var adminAuditLogRepository: AdminAuditLogRepository

    private fun mockMvc(): MockMvc =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    @Test
    fun `미인증으로 기능 요청 페이지에 접근하면 로그인 페이지로 리다이렉트된다`() {
        mockMvc()
            .perform(get("/admin/feature-requests"))
            .andExpect(status().is3xxRedirection)
    }

    @Test
    fun `로그인하면 기능 요청 페이지가 200 으로 렌더된다`() {
        mockMvc()
            .perform(get("/admin/feature-requests").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk)
    }

    @Test
    fun `CSRF 토큰 없이 요청을 등록하면 403 이 반환된다`() {
        mockMvc()
            .perform(
                post("/admin/feature-requests")
                    .with(user("admin").roles("ADMIN"))
                    .param("title", "새 기능"),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `요청을 등록하면 NEW 로 저장되고 감사 로그에 SUCCESS 로 기록된 뒤 목록으로 리다이렉트된다`() {
        mockMvc()
            .perform(
                post("/admin/feature-requests")
                    .with(user("admin").roles("ADMIN"))
                    .with(csrf())
                    .param("title", "item 목록 추출 실패 필터"),
            ).andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/feature-requests"))
            .andExpect(flash().attributeExists("message"))

        val saved = repository.findRecent(PageRequest.of(0, 10))
        assertEquals(1, saved.size)
        assertEquals("item 목록 추출 실패 필터", saved.first().title)
        assertEquals(AdminFeatureRequestStatus.NEW, saved.first().status)

        // findAll 은 순서를 보장하지 않으므로 방금 요청의 로그를 action/tool 로 특정해 단언한다.
        val log =
            adminAuditLogRepository.findAll().single {
                it.toolName == "feature-requests" && it.actionType == "CREATE_FEATURE_REQUEST"
            }
        assertEquals("SUCCESS", log.resultStatus)
        assertEquals("admin", log.adminUsername)
    }

    @Test
    fun `공백만 있는 제목으로 등록하면 저장되지 않고 오류 메시지와 함께 리다이렉트된다`() {
        mockMvc()
            .perform(
                post("/admin/feature-requests")
                    .with(user("admin").roles("ADMIN"))
                    .with(csrf())
                    .param("title", "   "),
            ).andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/feature-requests"))
            .andExpect(flash().attributeExists("error"))

        assertEquals(0, repository.findRecent(PageRequest.of(0, 10)).size)
    }

    @Test
    fun `상태를 토글하면 NEW 에서 DONE 으로 바뀐다`() {
        val saved = repository.save(AdminFeatureRequest.create("토글 대상", "admin"))
        val id = saved.getId()

        mockMvc()
            .perform(
                post("/admin/feature-requests/{id}/status", id)
                    .with(user("admin").roles("ADMIN"))
                    .with(csrf()),
            ).andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/feature-requests"))
            .andExpect(flash().attributeExists("message"))

        assertEquals(AdminFeatureRequestStatus.DONE, repository.findById(id)?.status)
    }
}
