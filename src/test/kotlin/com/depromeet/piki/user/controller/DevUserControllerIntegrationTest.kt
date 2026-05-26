package com.depromeet.piki.user.controller

import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.uuidToBytes
import com.depromeet.piki.user.domain.IdentityType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

@Transactional
class DevUserControllerIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `유저가 존재할 때 목록 조회 시 200 과 userId·nickname 목록이 반환된다`() {
        val userId = UUID.randomUUID()
        insertUser(userId, "테스트유저", IdentityType.MEMBER)

        buildMockMvc()
            .perform(get("/api/v1/dev/users"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data[?(@.userId == '${userId}')].nickname").value("테스트유저"))
            .andExpect(jsonPath("$.pageResponse.hasNext").value(false))
    }

    @Test
    fun `유저가 없을 때 목록 조회 시 200 과 빈 배열이 반환된다`() {
        buildMockMvc()
            .perform(get("/api/v1/dev/users"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data").isEmpty)
            .andExpect(jsonPath("$.pageResponse.hasNext").value(false))
    }

    @Test
    fun `size 초과 유저가 있으면 hasNext 가 true 이고 nextCursor 가 반환된다`() {
        repeat(3) { insertUser(UUID.randomUUID(), "유저$it", IdentityType.MEMBER) }

        buildMockMvc()
            .perform(get("/api/v1/dev/users").param("size", "2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.pageResponse.hasNext").value(true))
            .andExpect(jsonPath("$.pageResponse.nextCursor").value("1"))
    }

    @Test
    fun `존재하는 userId 로 단건 조회 시 200 과 전체 user 정보가 반환된다`() {
        val userId = UUID.randomUUID()
        insertUser(userId, "단건유저", IdentityType.MEMBER)

        buildMockMvc()
            .perform(get("/api/v1/dev/users/$userId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.id").value(userId.toString()))
            .andExpect(jsonPath("$.data.nickname").value("단건유저"))
            .andExpect(jsonPath("$.data.profileImage").exists())
            .andExpect(jsonPath("$.data.identityType").value("MEMBER"))
    }

    @Test
    fun `존재하지 않는 userId 로 단건 조회 시 404 가 ApiResponseBody 로 반환된다`() {
        buildMockMvc()
            .perform(get("/api/v1/dev/users/${UUID.randomUUID()}"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.data").doesNotExist())
    }

    @Test
    fun `인증 토큰 없이 목록 조회 시 200 이 반환된다`() {
        buildMockMvc()
            .perform(get("/api/v1/dev/users"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))
    }

    private fun buildMockMvc(): MockMvc =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    private fun insertUser(
        userId: UUID,
        nickname: String,
        identityType: IdentityType,
    ) {
        jdbcTemplate.update(
            "INSERT INTO users (id, nickname, profile_image, identity_type, created_at, updated_at, deleted_at) " +
                "VALUES (?, ?, ?, ?, NOW(6), NOW(6), NULL)",
            uuidToBytes(userId),
            nickname,
            "https://api.dicebear.com/9.x/bottts/svg?seed=$userId",
            identityType.name,
        )
    }
}
