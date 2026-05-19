package com.depromeet.team3.auth.controller

import com.depromeet.team3.auth.infrastructure.jwt.JwtProvider
import com.depromeet.team3.auth.infrastructure.redis.RefreshTokenStore
import com.depromeet.team3.support.IntegrationTestSupport
import com.depromeet.team3.support.uuidToBytes
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.util.UUID

class AuthTokenStateIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var refreshTokenStore: RefreshTokenStore

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private fun mockMvc() =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    private fun createGuest(): Pair<String, String> {
        val result =
            mockMvc()
                .perform(post("/api/v1/auth/guest").contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val json = objectMapper.readTree(result.response.contentAsString)
        return json.at("/data/accessToken").asText() to json.at("/data/refreshToken").asText()
    }

    private fun cleanup(userId: UUID) {
        refreshTokenStore.delete(userId)
        jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", uuidToBytes(userId))
    }

    @Test
    fun `POST auth token refresh - rotation 후 기존 refreshToken 재사용 시 401 이 반환된다`() {
        val mockMvc = mockMvc()
        val (accessToken, oldRefreshToken) = createGuest()
        val userId = jwtProvider.parseAccessToken(accessToken)?.userId ?: error("accessToken 파싱 실패")

        try {
            val oldBody = objectMapper.writeValueAsString(mapOf("refreshToken" to oldRefreshToken))
            val newRefreshToken =
                objectMapper
                    .readTree(
                        mockMvc
                            .perform(
                                post("/api/v1/auth/token/refresh")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(oldBody),
                            ).andExpect(status().isOk)
                            .andReturn()
                            .response
                            .contentAsString,
                    ).at("/data/refreshToken")
                    .asText()

            mockMvc
                .perform(
                    post("/api/v1/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oldBody),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.status").value(401))

            mockMvc
                .perform(
                    post("/api/v1/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("refreshToken" to newRefreshToken))),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.refreshToken").isString)
        } finally {
            cleanup(userId)
        }
    }

    @Test
    fun `POST auth logout - 로그아웃 후 refreshToken 으로 refresh 시도 시 401 이 반환된다`() {
        val mockMvc = mockMvc()
        val (accessToken, refreshToken) = createGuest()
        val userId = jwtProvider.parseAccessToken(accessToken)?.userId ?: error("accessToken 파싱 실패")

        try {
            mockMvc
                .perform(
                    post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                ).andExpect(status().isOk)

            mockMvc
                .perform(
                    post("/api/v1/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken))),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.status").value(401))
        } finally {
            cleanup(userId)
        }
    }
}
