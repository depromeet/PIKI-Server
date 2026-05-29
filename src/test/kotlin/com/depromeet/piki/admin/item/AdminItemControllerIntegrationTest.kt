package com.depromeet.piki.admin.item

import com.depromeet.piki.admin.audit.AdminAuditLogRepository
import com.depromeet.piki.item.repository.ItemRepository
import com.depromeet.piki.support.IntegrationTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
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
class AdminItemControllerIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var itemRepository: ItemRepository

    @Autowired
    private lateinit var adminAuditLogRepository: AdminAuditLogRepository

    private fun mockMvc(): MockMvc =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    @Test
    fun `미인증으로 실험 데이터 페이지에 접근하면 로그인 페이지로 리다이렉트된다`() {
        mockMvc()
            .perform(get("/admin/items"))
            .andExpect(status().is3xxRedirection)
    }

    @Test
    fun `로그인하면 실험 데이터 페이지가 200 으로 렌더된다`() {
        mockMvc()
            .perform(get("/admin/items").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk)
    }

    @Test
    fun `CSRF 토큰 없이 샘플 추가를 요청하면 403 이 반환된다`() {
        mockMvc()
            .perform(
                post("/admin/items/samples")
                    .with(user("admin").roles("ADMIN"))
                    .param("count", "3"),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `샘플 추가는 DB 에 반영되고 감사 로그에 SUCCESS 로 기록된 뒤 목록으로 리다이렉트된다`() {
        mockMvc()
            .perform(
                post("/admin/items/samples")
                    .with(user("admin").roles("ADMIN"))
                    .with(csrf())
                    .param("count", "3")
                    .param("namePrefix", "테스트상품"),
            ).andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/items"))
            .andExpect(flash().attributeExists("message"))

        assertEquals(3, itemRepository.findRecent(10).size)

        val logs = adminAuditLogRepository.findAll()
        assertEquals(1, logs.size)
        assertEquals("SUCCESS", logs.first().resultStatus)
        assertEquals("items", logs.first().toolName)
        assertEquals("INSERT_SAMPLE_ITEMS", logs.first().actionType)
    }

    @Test
    fun `허용 범위를 벗어난 개수는 추가되지 않고 오류 메시지와 함께 리다이렉트된다`() {
        mockMvc()
            .perform(
                post("/admin/items/samples")
                    .with(user("admin").roles("ADMIN"))
                    .with(csrf())
                    .param("count", "0"),
            ).andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/items"))
            .andExpect(flash().attributeExists("error"))

        assertEquals(0, itemRepository.findRecent(10).size)
    }
}
