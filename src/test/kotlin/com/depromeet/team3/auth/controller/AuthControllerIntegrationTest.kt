package com.depromeet.team3.auth.controller

import com.depromeet.team3.support.IntegrationTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper

@Transactional
class AuthControllerIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun mockMvc() =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    @Test
    fun `POST auth guest - 201 과 accessToken, refreshToken, user 가 응답에 포함된다`() {
        // FE 의 진입 fill 흐름을 위해 user 정보가 응답에 같이 와야 한다. 회귀 가드.
        mockMvc()
            .perform(post("/api/v1/auth/guest").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.code").value("CREATED"))
            .andExpect(jsonPath("$.data.accessToken").isString)
            .andExpect(jsonPath("$.data.refreshToken").isString)
            .andExpect(jsonPath("$.data.user.id").isString)
            .andExpect(jsonPath("$.data.user.nickname").isString)
            .andExpect(jsonPath("$.data.user.profileImage").isString)
            .andExpect(jsonPath("$.data.user.identityType").value("GUEST"))
    }

    @Test
    fun `POST auth token refresh - 유효한 refreshToken 으로 새 토큰 쌍이 발급된다`() {
        val guestResult =
            mockMvc()
                .perform(post("/api/v1/auth/guest").contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val refreshToken =
            objectMapper
                .readTree(guestResult.response.contentAsString)
                .at("/data/refreshToken")
                .asText()

        val body = objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken))
        mockMvc()
            .perform(
                post("/api/v1/auth/token/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.accessToken").isString)
            .andExpect(jsonPath("$.data.refreshToken").isString)
    }

    @Test
    fun `POST auth token refresh - 위조된 refreshToken 으로 401 이 반환된다`() {
        val body = objectMapper.writeValueAsString(mapOf("refreshToken" to "invalid.token.value"))
        mockMvc()
            .perform(
                post("/api/v1/auth/token/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `POST auth token refresh - refreshToken 이 빈 문자열이면 400 이 반환된다`() {
        val body = objectMapper.writeValueAsString(mapOf("refreshToken" to ""))
        mockMvc()
            .perform(
                post("/api/v1/auth/token/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
    }

    @Test
    fun `POST auth logout - 인증된 사용자가 로그아웃하면 200 이 반환된다`() {
        val guestResult =
            mockMvc()
                .perform(post("/api/v1/auth/guest").contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val accessToken =
            objectMapper
                .readTree(guestResult.response.contentAsString)
                .at("/data/accessToken")
                .asText()

        mockMvc()
            .perform(
                post("/api/v1/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))
    }

    @Test
    fun `POST auth logout - Authorization 헤더 없으면 401 이 반환된다`() {
        mockMvc()
            .perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized)
    }
}
