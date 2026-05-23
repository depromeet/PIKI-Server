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

class HealthControllerIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Test
    fun `GET health - 인증 없이 200 응답이 와야 한다 (배포 워크플로우 health check 회귀 가드)`() {
        // anyRequest().authenticated() 가 적용된 상태에서 /health 가 permitAll 명시 누락 시
        // 401 응답으로 배포가 차단된다. contract 가드로 통합 테스트에서 직접 검증한다.
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        mockMvc
            .perform(get("/health"))
            .andExpect(status().isOk)
    }
}
