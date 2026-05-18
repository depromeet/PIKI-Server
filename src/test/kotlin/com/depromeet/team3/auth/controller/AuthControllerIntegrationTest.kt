package com.depromeet.team3.auth.controller

import com.depromeet.team3.auth.infrastructure.jwt.JwtProvider
import com.depromeet.team3.auth.infrastructure.redis.RefreshTokenStore
import com.depromeet.team3.support.IntegrationTestSupport
import com.jayway.jsonpath.JsonPath
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
import java.util.UUID
import kotlin.test.assertNull

@Transactional
class AuthControllerIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var refreshTokenStore: RefreshTokenStore

    @Test
    fun `POST auth-guest - GUEST 유저가 생성되고 액세스-리프레시 토큰이 발급된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(
                    webApplicationContext,
                ).apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        mockMvc
            .perform(post("/auth/guest"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.data.accessToken").isString)
            .andExpect(jsonPath("$.data.refreshToken").isString)
    }

    @Test
    fun `POST auth-token-refresh - 유효한 리프레시 토큰으로 새 토큰 쌍이 발급된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(
                    webApplicationContext,
                ).apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        val guestResponse =
            mockMvc
                .perform(post("/auth/guest"))
                .andReturn()
                .response.contentAsString
        val refreshToken = JsonPath.read<String>(guestResponse, "$.data.refreshToken")

        mockMvc
            .perform(
                post("/auth/token/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken))),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.accessToken").isString)
            .andExpect(jsonPath("$.data.refreshToken").isString)
    }

    @Test
    fun `POST auth-token-refresh - 위조된 토큰이면 401 이 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(
                    webApplicationContext,
                ).apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        mockMvc
            .perform(
                post("/auth/token/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("refreshToken" to "invalid.token.value"))),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value(401))
    }

    @Test
    fun `POST auth-token-refresh - Redis 에 없는 토큰이면 401 이 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(
                    webApplicationContext,
                ).apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val orphanToken = jwtProvider.generateRefreshToken(UUID.randomUUID())

        mockMvc
            .perform(
                post("/auth/token/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("refreshToken" to orphanToken))),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value(401))
    }

    @Test
    fun `POST auth-logout - 인증된 유저가 로그아웃하면 Redis 토큰이 삭제된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(
                    webApplicationContext,
                ).apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        val guestResponse =
            mockMvc
                .perform(post("/auth/guest"))
                .andReturn()
                .response.contentAsString
        val accessToken = JsonPath.read<String>(guestResponse, "$.data.accessToken")
        val userId = jwtProvider.getUserIdFromToken(accessToken)

        mockMvc
            .perform(
                post("/auth/logout")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))

        assertNull(refreshTokenStore.get(userId))
    }

    @Test
    fun `POST auth-logout - 토큰 없이 요청하면 401 이 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(
                    webApplicationContext,
                ).apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        mockMvc
            .perform(post("/auth/logout"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value(401))
    }
}
