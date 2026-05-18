package com.depromeet.team3.wishlist.controller

import com.depromeet.team3.auth.infrastructure.jwt.JwtProvider
import com.depromeet.team3.product.domain.ProductSnapshot
import com.depromeet.team3.support.IntegrationTestSupport
import com.depromeet.team3.support.StubProductExtractor
import com.depromeet.team3.support.uuidToBytes
import com.depromeet.team3.user.domain.IdentityType
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
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Transactional
class WishlistControllerIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var stubExtractor: StubProductExtractor

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    private fun insertUser(userId: UUID) {
        val bytes = uuidToBytes(userId)
        jdbcTemplate.update(
            "INSERT INTO user (id, nickname, identity_type, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6))",
            bytes,
            "테스트유저",
            "GUEST",
        )
    }

    private fun bearerToken(userId: UUID): String =
        "Bearer ${jwtProvider.generateAccessToken(userId, IdentityType.GUEST)}"

    @Test
    fun `정상 등록 - 201 과 함께 추출 결과가 응답에 박힌다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(
                    webApplicationContext,
                ).apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val url = "https://shop.example.com/products/42"
        val userId = UUID.randomUUID()
        insertUser(userId)
        stubExtractor.build = { link ->
            ProductSnapshot(
                link = link,
                name = "나이키 에어포스",
                currentPrice = 99_000,
                currency = "KRW",
                imageUrl = "https://cdn.example.com/p/42.jpg",
            )
        }
        val body = objectMapper.writeValueAsString(mapOf("url" to url, "userId" to userId))

        mockMvc
            .perform(
                post("/api/v1/wishlists")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.code").value("CREATED"))
            .andExpect(jsonPath("$.data.wishId").isNumber)
            .andExpect(jsonPath("$.data.name").value("나이키 에어포스"))
            .andExpect(jsonPath("$.data.currentPrice").value(99_000))
            .andExpect(jsonPath("$.data.currency").value("KRW"))
            .andExpect(jsonPath("$.data.imageUrl").value("https://cdn.example.com/p/42.jpg"))
    }

    @Test
    fun `같은 유저가 같은 URL 을 두 번 등록하면 409 CONFLICT 가 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(
                    webApplicationContext,
                ).apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val url = "https://shop.example.com/products/42"
        val userId = UUID.randomUUID()
        insertUser(userId)
        stubExtractor.build = { ProductSnapshot(link = it, name = "기본 상품") }
        val body = objectMapper.writeValueAsString(mapOf("url" to url, "userId" to userId))

        mockMvc
            .perform(
                post("/api/v1/wishlists")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isCreated)

        mockMvc
            .perform(
                post("/api/v1/wishlists")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.code").value("CONFLICT"))
            .andExpect(jsonPath("$.data").doesNotExist())
    }

    @Test
    fun `다른 유저가 같은 URL 을 등록하면 둘 다 201 로 등록된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(
                    webApplicationContext,
                ).apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val url = "https://shop.example.com/products/42"
        stubExtractor.build = { ProductSnapshot(link = it, name = "기본 상품") }
        val firstUserId = UUID.randomUUID()
        val secondUserId = UUID.randomUUID()
        insertUser(firstUserId)
        insertUser(secondUserId)
        val firstBody = objectMapper.writeValueAsString(mapOf("url" to url, "userId" to firstUserId))
        val secondBody = objectMapper.writeValueAsString(mapOf("url" to url, "userId" to secondUserId))

        mockMvc
            .perform(
                post("/api/v1/wishlists")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(firstUserId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(firstBody),
            ).andExpect(status().isCreated)

        mockMvc
            .perform(
                post("/api/v1/wishlists")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(secondUserId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(secondBody),
            ).andExpect(status().isCreated)
    }

    @Test
    fun `url 이 빈 문자열이면 400 BAD_REQUEST 가 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(
                    webApplicationContext,
                ).apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertUser(userId)
        val body = objectMapper.writeValueAsString(mapOf("url" to "", "userId" to userId))

        mockMvc
            .perform(
                post("/api/v1/wishlists")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `인증 토큰 없이 요청하면 401 이 반환된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(
                    webApplicationContext,
                ).apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val body =
            objectMapper.writeValueAsString(
                mapOf(
                    "url" to "https://shop.example.com/products/42",
                    "userId" to UUID.randomUUID(),
                ),
            )

        mockMvc
            .perform(
                post("/api/v1/wishlists")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value(401))
    }
}
