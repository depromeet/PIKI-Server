package com.depromeet.piki.auth.controller

import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.auth.web.ClientType
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.uuidToBytes
import com.depromeet.piki.user.domain.IdentityType
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

@Transactional
class DevAuthControllerIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `app - MEMBER user 의 ID 로 토큰 발급 시 201 과 body 에 access·refresh 토큰이 반환된다`() {
        val targetUserId = UUID.randomUUID()
        insertUser(targetUserId, IdentityType.MEMBER, deleted = false)
        val guestToken = jwtProvider.generateAccessToken(UUID.randomUUID(), IdentityType.GUEST)

        buildMockMvc()
            .perform(
                post("/api/v1/dev/$targetUserId/token")
                    .header(ClientType.HEADER, "app")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $guestToken"),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.accessToken", notNullValue()))
            .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
            .andExpect(jsonPath("$.data.user.id").value(targetUserId.toString()))
            .andExpect(jsonPath("$.data.user.identityType").value("MEMBER"))
    }

    @Test
    fun `web(기본) - 토큰 발급 시 Set-Cookie 로 내리고 body 토큰은 null 이다`() {
        // dev 엔드포인트도 FE 웹 팀이 쿠키 흐름으로 테스트할 수 있어야 하므로 양쪽을 모두 검증한다.
        val targetUserId = UUID.randomUUID()
        insertUser(targetUserId, IdentityType.MEMBER, deleted = false)
        val guestToken = jwtProvider.generateAccessToken(UUID.randomUUID(), IdentityType.GUEST)

        buildMockMvc()
            .perform(
                post("/api/v1/dev/$targetUserId/token")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $guestToken"),
            ).andExpect(status().isCreated)
            .andExpect(cookie().exists("access_token"))
            .andExpect(cookie().exists("refresh_token"))
            .andExpect(jsonPath("$.data.accessToken").value(nullValue()))
            .andExpect(jsonPath("$.data.refreshToken").value(nullValue()))
            .andExpect(jsonPath("$.data.user.identityType").value("MEMBER"))
    }

    @Test
    fun `GUEST user 의 ID 로 토큰 발급 요청 시 201 과 GUEST 권한 토큰 쌍이 반환된다`() {
        val targetUserId = UUID.randomUUID()
        insertUser(targetUserId, IdentityType.GUEST, deleted = false)
        val callerToken = jwtProvider.generateAccessToken(UUID.randomUUID(), IdentityType.GUEST)

        buildMockMvc()
            .perform(
                post("/api/v1/dev/$targetUserId/token")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $callerToken"),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.user.id").value(targetUserId.toString()))
            .andExpect(jsonPath("$.data.user.identityType").value("GUEST"))
    }

    @Test
    fun `존재하지 않는 userId 로 토큰 발급 요청 시 404 가 ApiResponseBody 로 반환된다`() {
        val unknownUserId = UUID.randomUUID()
        val guestToken = jwtProvider.generateAccessToken(UUID.randomUUID(), IdentityType.GUEST)

        buildMockMvc()
            .perform(
                post("/api/v1/dev/$unknownUserId/token")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $guestToken"),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.detail", notNullValue()))
            .andExpect(jsonPath("$.data").doesNotExist())
    }

    @Test
    fun `탈퇴된 user 의 ID 로 토큰 발급 요청 시 409 가 ApiResponseBody 로 반환된다`() {
        val deletedUserId = UUID.randomUUID()
        insertUser(deletedUserId, IdentityType.MEMBER, deleted = true)
        val guestToken = jwtProvider.generateAccessToken(UUID.randomUUID(), IdentityType.GUEST)

        buildMockMvc()
            .perform(
                post("/api/v1/dev/$deletedUserId/token")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $guestToken"),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.detail", notNullValue()))
            .andExpect(jsonPath("$.data").doesNotExist())
    }

    private fun buildMockMvc(): MockMvc =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    private fun insertUser(
        userId: UUID,
        identityType: IdentityType,
        deleted: Boolean,
    ) {
        val deletedAtClause = if (deleted) "NOW(6)" else "NULL"
        jdbcTemplate.update(
            "INSERT INTO users (id, nickname, identity_type, created_at, updated_at, deleted_at) " +
                "VALUES (?, ?, ?, NOW(6), NOW(6), $deletedAtClause)",
            uuidToBytes(userId),
            userId.toString().take(10),
            identityType.name,
        )
    }
}
