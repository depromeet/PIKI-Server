package com.depromeet.piki.auth.controller

import com.depromeet.piki.support.IntegrationTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// Apple 웹 form_post 콜백 브릿지(#430). Apple 의 POST(form_post)를 받아 프론트 공용 콜백으로 GET 쿼리 302 하는지,
// 로그인은 하지 않고 code·state 를 그대로 넘기는지(state 미소비) 검증한다.
// webCallbackUrl 은 test application.yml 의 oauth.apple.web-callback-url = https://test.piki.day/auth/callback/apple.
class AppleCallbackBridgeIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    private fun mockMvc() =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    @Test
    fun `Apple form_post 콜백은 인증 없이 code·state 를 프론트 공용 콜백으로 302 한다`() {
        mockMvc()
            .perform(
                post("/api/v1/auth/apple/callback")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("code", "apple-auth-code")
                    .param("state", "csrf-state-1"),
            ).andExpect(status().isFound)
            .andExpect(
                redirectedUrl("https://test.piki.day/auth/callback/apple?code=apple-auth-code&state=csrf-state-1"),
            )
    }

    @Test
    fun `Apple 이 error 를 form_post 로 보내면 프론트 공용 콜백으로 error 를 실어 302 한다`() {
        mockMvc()
            .perform(
                post("/api/v1/auth/apple/callback")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("error", "user_cancelled"),
            ).andExpect(status().isFound)
            .andExpect(redirectedUrl("https://test.piki.day/auth/callback/apple?error=user_cancelled"))
    }
}
