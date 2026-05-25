package com.depromeet.piki.user.controller

import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.uuidToBytes
import com.depromeet.piki.user.domain.IdentityType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Transactional
class UserControllerIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    private fun insertUser(
        userId: UUID,
        nickname: String = userId.toString().take(10),
        identityType: IdentityType = IdentityType.GUEST,
    ) {
        jdbcTemplate.update(
            "INSERT INTO users (id, nickname, profile_image, identity_type, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, NOW(6), NOW(6))",
            uuidToBytes(userId),
            nickname,
            "https://example.com/img.png",
            identityType.name,
        )
    }

    private fun token(
        userId: UUID,
        identityType: IdentityType = IdentityType.GUEST,
    ): String = jwtProvider.generateAccessToken(userId, identityType)

    @Test
    fun `GET users me - 게스트가 자기 정보를 200 으로 조회한다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId, nickname = "테스트닉네임", identityType = IdentityType.GUEST)

        mockMvc
            .perform(
                get("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId, IdentityType.GUEST)}"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.id").value(userId.toString()))
            .andExpect(jsonPath("$.data.nickname").value("테스트닉네임"))
            .andExpect(jsonPath("$.data.identityType").value("GUEST"))
            .andExpect(jsonPath("$.data.profileImage").isString)
    }

    @Test
    fun `GET users me - 인증 헤더 없으면 401 이 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `PATCH users me - 게스트가 닉네임을 수정하면 200 과 변경된 닉네임이 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId, nickname = "초기닉네임", identityType = IdentityType.GUEST)
        val body = objectMapper.writeValueAsString(mapOf("nickname" to "새닉네임"))

        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId, IdentityType.GUEST)}")
                    .content(body),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
    }

    @Test
    fun `PATCH users me - 닉네임 11자는 400 이 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId)
        val body = objectMapper.writeValueAsString(mapOf("nickname" to "12345678901"))

        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId)}")
                    .content(body),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `PATCH users me - 이미 사용 중인 닉네임으로 변경하면 409 가 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val otherUserId = UUID.randomUUID()
        insertUser(otherUserId, nickname = "점유닉네임")
        val myUserId = UUID.randomUUID()
        insertUser(myUserId, nickname = "내닉네임")
        val body = objectMapper.writeValueAsString(mapOf("nickname" to "점유닉네임"))

        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(myUserId)}")
                    .content(body),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CONFLICT"))
    }

    @Test
    fun `GET users nickname check - 사용 가능한 닉네임이면 available true 가 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId)

        mockMvc
            .perform(
                get("/api/v1/users/nickname/check")
                    .param("nickname", "안쓰는닉네임")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId)}"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.available").value(true))
    }

    @Test
    fun `GET users nickname check - 이미 사용 중인 닉네임이면 available false 가 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId, nickname = "점유닉네임")

        mockMvc
            .perform(
                get("/api/v1/users/nickname/check")
                    .param("nickname", "점유닉네임")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId)}"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.available").value(false))
    }

    @Test
    fun `GET users nickname check - 11자 닉네임은 400 이 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId)

        mockMvc
            .perform(
                get("/api/v1/users/nickname/check")
                    .param("nickname", "12345678901")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(userId)}"),
            ).andExpect(status().isBadRequest)
    }
}
