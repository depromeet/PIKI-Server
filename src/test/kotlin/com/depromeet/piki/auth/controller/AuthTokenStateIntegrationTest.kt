package com.depromeet.piki.auth.controller

import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.auth.infrastructure.redis.RefreshTokenStore
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.uuidToBytes
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
                .perform(post("/api/v1/auth/guest").contentType(MediaType.APPLICATION_JSON).header("X-Client-Type", "app"))
                .andReturn()
        val json = objectMapper.readTree(result.response.contentAsString)
        return json.at("/data/accessToken").asText() to json.at("/data/refreshToken").asText()
    }

    private fun cleanup(userId: UUID) {
        refreshTokenStore.delete(userId)
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", uuidToBytes(userId))
    }

    @Test
    fun `POST auth token refresh - 정상 rotation 흐름으로 새 refreshToken 을 연속 사용할 수 있다`() {
        // 도난 없는 정상 흐름의 baseline: R1 → R2 → R3 까지 연속 회전 가능.
        val mockMvc = mockMvc()
        val (accessToken, r1) = createGuest()
        val userId = jwtProvider.parseAccessToken(accessToken)?.userId ?: error("accessToken 파싱 실패")

        try {
            val r2 =
                objectMapper
                    .readTree(
                        mockMvc
                            .perform(
                                post("/api/v1/auth/token/refresh")
                                    .header("X-Client-Type", "app")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(mapOf("refreshToken" to r1))),
                            ).andExpect(status().isOk)
                            .andReturn()
                            .response
                            .contentAsString,
                    ).at("/data/refreshToken")
                    .asText()

            mockMvc
                .perform(
                    post("/api/v1/auth/token/refresh")
                        .header("X-Client-Type", "app")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("refreshToken" to r2))),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.refreshToken").isString)
        } finally {
            cleanup(userId)
        }
    }

    @Test
    fun `POST auth token refresh - 재사용된 옛 refreshToken 으로 시도하면 401 + 살아있던 R2 도 무효화 (family invalidation)`() {
        // OAuth 2.0 RFC 6819 / 8252 의 family invalidation 패턴 검증:
        // R1 으로 회전해서 R2 가 살아있는 상태에서 R1 을 다시 쓰면 (= 도난 의심) 양쪽 다 끊는다.
        // 공격자가 옛 R1 을 들고 와도, 정상 사용자가 옛 R1 을 실수로 재시도해도 동일하게 처리된다.
        val mockMvc = mockMvc()
        val (accessToken, r1) = createGuest()
        val userId = jwtProvider.parseAccessToken(accessToken)?.userId ?: error("accessToken 파싱 실패")

        try {
            val r1Body = objectMapper.writeValueAsString(mapOf("refreshToken" to r1))
            val r2 =
                objectMapper
                    .readTree(
                        mockMvc
                            .perform(
                                post("/api/v1/auth/token/refresh")
                                    .header("X-Client-Type", "app")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(r1Body),
                            ).andExpect(status().isOk)
                            .andReturn()
                            .response
                            .contentAsString,
                    ).at("/data/refreshToken")
                    .asText()

            // R1 재사용 시도 → 401 + family invalidation 트리거 (살아있던 R2 도 같이 DEL)
            mockMvc
                .perform(
                    post("/api/v1/auth/token/refresh")
                        .header("X-Client-Type", "app")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(r1Body),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.status").value(401))

            // R2 도 무효화됐는지 확인 — 정상 흐름이면 200 이어야 하지만 family invalidation 으로 401
            mockMvc
                .perform(
                    post("/api/v1/auth/token/refresh")
                        .header("X-Client-Type", "app")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("refreshToken" to r2))),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.status").value(401))
        } finally {
            cleanup(userId)
        }
    }

    @Test
    fun `POST auth token refresh - 공격자가 먼저 R1 으로 R2 를 받은 뒤 정상 사용자가 R1 을 시도해도 양쪽 다 무효화된다`() {
        // 경우 2 시뮬레이션: 공격자가 R1 으로 R2 를 받아 가버리면 정상 사용자가 R1 을 들고와도
        // family invalidation 이 트리거돼 공격자가 받아간 R2 까지 함께 죽는다.
        val mockMvc = mockMvc()
        val (accessToken, r1) = createGuest()
        val userId = jwtProvider.parseAccessToken(accessToken)?.userId ?: error("accessToken 파싱 실패")

        try {
            val r1Body = objectMapper.writeValueAsString(mapOf("refreshToken" to r1))

            // 공격자: R1 으로 R2 를 받아간다.
            val attackerR2 =
                objectMapper
                    .readTree(
                        mockMvc
                            .perform(
                                post("/api/v1/auth/token/refresh")
                                    .header("X-Client-Type", "app")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(r1Body),
                            ).andExpect(status().isOk)
                            .andReturn()
                            .response
                            .contentAsString,
                    ).at("/data/refreshToken")
                    .asText()

            // 정상 사용자: 자기 R1 으로 시도 → 401 + 공격자의 R2 도 무효화
            mockMvc
                .perform(
                    post("/api/v1/auth/token/refresh")
                        .header("X-Client-Type", "app")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(r1Body),
                ).andExpect(status().isUnauthorized)

            // 공격자도 끊긴다
            mockMvc
                .perform(
                    post("/api/v1/auth/token/refresh")
                        .header("X-Client-Type", "app")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("refreshToken" to attackerR2))),
                ).andExpect(status().isUnauthorized)
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
                        .header("X-Client-Type", "app")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken))),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.status").value(401))
        } finally {
            cleanup(userId)
        }
    }
}
