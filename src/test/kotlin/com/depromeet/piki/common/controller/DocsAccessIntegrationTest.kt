package com.depromeet.piki.common.controller

import com.depromeet.piki.support.IntegrationTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

class DocsAccessIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Test
    fun `GET docs index html - 인증 없이 200 응답이 와야 한다 (Stoplight UI 접근 회귀 가드)`() {
        // anyRequest().authenticated() 가 적용된 상태에서 /docs/** 가 permitAll 명시 누락 시
        // 401 응답으로 운영 docs UI (https://api.depromeet18team3.cloud/docs/index.html) 가 차단된다.
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        mockMvc
            .perform(get("/docs/index.html"))
            .andExpect(status().isOk)
    }

    @Test
    fun `GET v3 api-docs - 인증 없이 200 응답이 와야 한다 (Stoplight 가 fetch 하는 OpenAPI spec)`() {
        // Stoplight UI 가 동작하려면 spec endpoint 도 인증 없이 접근 가능해야 한다.
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
    }
}
