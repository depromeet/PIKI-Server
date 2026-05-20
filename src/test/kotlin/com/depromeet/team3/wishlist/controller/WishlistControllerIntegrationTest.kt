package com.depromeet.team3.wishlist.controller

import com.depromeet.team3.auth.infrastructure.jwt.JwtProvider
import com.depromeet.team3.product.service.ProductSnapshot
import com.depromeet.team3.support.IntegrationTestSupport
import com.depromeet.team3.support.StubProductLinkExtractor
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
    private lateinit var stubProductLinkExtractor: StubProductLinkExtractor

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    private fun insertMember(userId: UUID) {
        jdbcTemplate.update(
            "INSERT INTO user (id, nickname, identity_type, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6))",
            uuidToBytes(userId),
            userId.toString().take(16),
            "MEMBER",
        )
    }

    private fun memberToken(userId: UUID): String = jwtProvider.generateAccessToken(userId, IdentityType.MEMBER)

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
        insertMember(userId)
        stubProductLinkExtractor.build = { link ->
            ProductSnapshot(
                link = link,
                name = "나이키 에어포스",
                currentPrice = 99_000,
                currency = "KRW",
                imageUrl = "https://cdn.example.com/p/42.jpg",
            )
        }
        val body = objectMapper.writeValueAsString(mapOf("url" to url))

        mockMvc
            .perform(
                post("/api/v1/wishlists")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(userId)}")
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
    fun `같은 유저가 같은 URL 을 두 번 등록해도 둘 다 201 로 등록된다`() {
        // dedup 정책은 #134 (item 독립 엔티티 분리) 에서 제거됨. wish 는 user 가 item 을 위시한 사건으로
        // 보는 multi-record 모델이라 같은 URL 을 반복 등록해도 별개의 wish row 로 쌓인다.
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(
                    webApplicationContext,
                ).apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val url = "https://shop.example.com/products/42"
        val userId = UUID.randomUUID()
        insertMember(userId)
        stubProductLinkExtractor.build = { ProductSnapshot(link = it, name = "기본 상품") }
        val body = objectMapper.writeValueAsString(mapOf("url" to url))
        val authHeader = "Bearer ${memberToken(userId)}"

        mockMvc
            .perform(
                post("/api/v1/wishlists")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .content(body),
            ).andExpect(status().isCreated)

        mockMvc
            .perform(
                post("/api/v1/wishlists")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .content(body),
            ).andExpect(status().isCreated)
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
        stubProductLinkExtractor.build = { ProductSnapshot(link = it, name = "기본 상품") }
        val firstUserId = UUID.randomUUID()
        val secondUserId = UUID.randomUUID()
        insertMember(firstUserId)
        insertMember(secondUserId)
        val body = objectMapper.writeValueAsString(mapOf("url" to url))

        mockMvc
            .perform(
                post("/api/v1/wishlists")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(firstUserId)}")
                    .content(body),
            ).andExpect(status().isCreated)

        mockMvc
            .perform(
                post("/api/v1/wishlists")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(secondUserId)}")
                    .content(body),
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
        insertMember(userId)
        val body = objectMapper.writeValueAsString(mapOf("url" to ""))

        mockMvc
            .perform(
                post("/api/v1/wishlists")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(userId)}")
                    .content(body),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `잘못된 형식의 url 은 400 BAD_REQUEST 로 응답되며 detail 에 원본 url 이 새지 않는다`() {
        // GlobalExceptionHandler 가 IllegalArgumentException_message 를 응답 detail 에 그대로 박는 구조라
        // ProductLink_parse 가 원본을 메시지에 담으면 쿼리스트링 토큰이 클라이언트 응답으로 새어 나간다.
        // ProductLink_parse 의 message 정책과 contract 양 끝을 함께 묶어 회귀를 잡는다.
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(
                    webApplicationContext,
                ).apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val userId = UUID.randomUUID()
        insertMember(userId)
        val rawWithSecret = "data:text/html,<token=SHOULD_NOT_LEAK>"
        val body = objectMapper.writeValueAsString(mapOf("url" to rawWithSecret))

        mockMvc
            .perform(
                post("/api/v1/wishlists")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(userId)}")
                    .content(body),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.detail").value("유효한 URL 형식이 아닙니다."))
    }
}
