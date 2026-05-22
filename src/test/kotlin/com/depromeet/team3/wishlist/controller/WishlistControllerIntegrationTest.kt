package com.depromeet.team3.wishlist.controller

import com.depromeet.team3.auth.infrastructure.jwt.JwtProvider
import com.depromeet.team3.common.domain.Product
import com.depromeet.team3.product.service.ProductSnapshot
import com.depromeet.team3.product.service.gemini.GeminiApiException
import com.depromeet.team3.support.IntegrationTestSupport
import com.depromeet.team3.support.StubProductImageExtractor
import com.depromeet.team3.support.StubProductLinkExtractor
import com.depromeet.team3.support.uuidToBytes
import com.depromeet.team3.user.domain.IdentityType
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
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
    private lateinit var stubProductImageExtractor: StubProductImageExtractor

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    private fun insertMember(userId: UUID) {
        jdbcTemplate.update(
            "INSERT INTO users (id, nickname, identity_type, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6))",
            uuidToBytes(userId),
            userId.toString().take(10),
            "MEMBER",
        )
    }

    private fun memberToken(userId: UUID): String = jwtProvider.generateAccessToken(userId, IdentityType.MEMBER)

    private fun buildMockMvc(): MockMvc =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    // POST 로 위시 한 건 등록하고 생성된 wishId 를 돌려준다. 조회 시나리오의 데이터 세팅용.
    private fun registerWish(
        mockMvc: MockMvc,
        authHeader: String,
        url: String,
        name: String,
    ): Long {
        stubProductLinkExtractor.build = { ProductSnapshot(link = it, name = name) }
        val body = objectMapper.writeValueAsString(mapOf("url" to url))
        val response =
            mockMvc
                .perform(
                    post("/api/v1/wishlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .content(body),
                ).andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return objectMapper
            .readTree(response)
            .path("data")
            .path("wish")
            .path("id")
            .asLong()
    }

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
            .andExpect(jsonPath("$.data.wish.id").isNumber)
            .andExpect(jsonPath("$.data.wish.createdAt").exists())
            .andExpect(jsonPath("$.data.item.name").value("나이키 에어포스"))
            .andExpect(jsonPath("$.data.item.currentPrice").value(99_000))
            .andExpect(jsonPath("$.data.item.currency").value("KRW"))
            .andExpect(jsonPath("$.data.item.imageUrl").value("https://cdn.example.com/p/42.jpg"))
            .andExpect(jsonPath("$.data.item.sourceUrl").value(url))
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

    @Test
    fun `위시리스트가 비어 있으면 빈 배열과 hasNext=false 를 반환한다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)

        mockMvc
            .perform(
                get("/api/v1/wishlists")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(userId)}"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.length()").value(0))
            .andExpect(jsonPath("$.pageResponse.hasNext").value(false))
            .andExpect(jsonPath("$.pageResponse.nextCursor").value(nullValue()))
    }

    @Test
    fun `본인 위시만 최신 등록순으로 반환하고 다른 유저 wish 는 섞이지 않는다`() {
        val mockMvc = buildMockMvc()
        val ownerId = UUID.randomUUID()
        val otherId = UUID.randomUUID()
        insertMember(ownerId)
        insertMember(otherId)
        val ownerAuth = "Bearer ${memberToken(ownerId)}"
        registerWish(mockMvc, ownerAuth, "https://shop.example.com/products/1", "첫 상품")
        val secondWishId = registerWish(mockMvc, ownerAuth, "https://shop.example.com/products/2", "둘째 상품")
        registerWish(mockMvc, "Bearer ${memberToken(otherId)}", "https://shop.example.com/products/3", "남의 상품")

        mockMvc
            .perform(
                get("/api/v1/wishlists")
                    .header(HttpHeaders.AUTHORIZATION, ownerAuth),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(2))
            // 최신 등록(둘째)이 맨 앞 (id desc)
            .andExpect(jsonPath("$.data[0].wish.id").value(secondWishId))
            .andExpect(jsonPath("$.data[0].item.name").value("둘째 상품"))
            .andExpect(jsonPath("$.data[1].item.name").value("첫 상품"))
            .andExpect(jsonPath("$.pageResponse.hasNext").value(false))
    }

    @Test
    fun `size 보다 많으면 hasNext 와 nextCursor 를 주고 그 cursor 로 다음 페이지를 잇는다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        val authHeader = "Bearer ${memberToken(userId)}"
        val firstWishId = registerWish(mockMvc, authHeader, "https://shop.example.com/products/1", "상품1")
        val secondWishId = registerWish(mockMvc, authHeader, "https://shop.example.com/products/2", "상품2")
        registerWish(mockMvc, authHeader, "https://shop.example.com/products/3", "상품3")

        // 첫 페이지: 최신 2건 + 다음 페이지 존재
        mockMvc
            .perform(
                get("/api/v1/wishlists")
                    .param("size", "2")
                    .header(HttpHeaders.AUTHORIZATION, authHeader),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.pageResponse.hasNext").value(true))
            .andExpect(jsonPath("$.pageResponse.nextCursor").value(secondWishId.toString()))

        // 다음 페이지: cursor 이전(=더 오래된) 1건, 더 이상 없음
        mockMvc
            .perform(
                get("/api/v1/wishlists")
                    .param("size", "2")
                    .param("cursor", secondWishId.toString())
                    .header(HttpHeaders.AUTHORIZATION, authHeader),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].wish.id").value(firstWishId))
            .andExpect(jsonPath("$.pageResponse.hasNext").value(false))
            .andExpect(jsonPath("$.pageResponse.nextCursor").value(nullValue()))
    }

    @Test
    fun `위시 item 의 이름·가격·이미지·통화를 수정하면 200 과 갱신된 값이 반환된다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        val authHeader = "Bearer ${memberToken(userId)}"
        val wishId = registerWish(mockMvc, authHeader, "https://shop.example.com/products/1", "잘못 읽힌 이름")
        val body =
            objectMapper.writeValueAsString(
                mapOf(
                    "name" to "교정된 이름",
                    "currentPrice" to 88_000,
                    "imageUrl" to "https://cdn.example.com/fixed.jpg",
                    "currency" to "KRW",
                ),
            )

        mockMvc
            .perform(
                patch("/api/v1/wishlists/$wishId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .content(body),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.wish.id").value(wishId))
            .andExpect(jsonPath("$.data.item.name").value("교정된 이름"))
            .andExpect(jsonPath("$.data.item.currentPrice").value(88_000))
            .andExpect(jsonPath("$.data.item.imageUrl").value("https://cdn.example.com/fixed.jpg"))
            .andExpect(jsonPath("$.data.item.currency").value("KRW"))
    }

    @Test
    fun `name 만 수정하면 currentPrice·currency·imageUrl 은 유지된다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        val authHeader = "Bearer ${memberToken(userId)}"
        stubProductLinkExtractor.build = {
            ProductSnapshot(
                link = it,
                name = "원래 이름",
                currentPrice = 50_000,
                currency = "KRW",
                imageUrl = "https://cdn.example.com/orig.jpg",
            )
        }
        val registerBody = objectMapper.writeValueAsString(mapOf("url" to "https://shop.example.com/products/1"))
        val wishId =
            objectMapper
                .readTree(
                    mockMvc
                        .perform(
                            post("/api/v1/wishlists")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(HttpHeaders.AUTHORIZATION, authHeader)
                                .content(registerBody),
                        ).andExpect(status().isCreated)
                        .andReturn()
                        .response
                        .getContentAsString(Charsets.UTF_8),
                ).path("data")
                .path("wish")
                .path("id")
                .asLong()

        mockMvc
            .perform(
                patch("/api/v1/wishlists/$wishId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .content(objectMapper.writeValueAsString(mapOf("name" to "새 이름"))),
            ).andExpect(status().isOk)
            // name 만 갱신, 나머지는 등록 시점 값 유지
            .andExpect(jsonPath("$.data.item.name").value("새 이름"))
            .andExpect(jsonPath("$.data.item.currentPrice").value(50_000))
            .andExpect(jsonPath("$.data.item.currency").value("KRW"))
            .andExpect(jsonPath("$.data.item.imageUrl").value("https://cdn.example.com/orig.jpg"))
    }

    @Test
    fun `남의 위시를 수정하면 403 이 반환된다`() {
        val mockMvc = buildMockMvc()
        val ownerId = UUID.randomUUID()
        val otherId = UUID.randomUUID()
        insertMember(ownerId)
        insertMember(otherId)
        val wishId =
            registerWish(mockMvc, "Bearer ${memberToken(ownerId)}", "https://shop.example.com/products/1", "내 상품")
        val body = objectMapper.writeValueAsString(mapOf("name" to "남이 바꾼 이름"))

        mockMvc
            .perform(
                patch("/api/v1/wishlists/$wishId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(otherId)}")
                    .content(body),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
    }

    @Test
    fun `존재하지 않는 위시를 수정하면 404 가 반환된다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        val body = objectMapper.writeValueAsString(mapOf("name" to "아무거나"))

        mockMvc
            .perform(
                patch("/api/v1/wishlists/99999999")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(userId)}")
                    .content(body),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `가격을 음수로 수정하면 400 BAD_REQUEST 가 반환된다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        val authHeader = "Bearer ${memberToken(userId)}"
        val wishId = registerWish(mockMvc, authHeader, "https://shop.example.com/products/1", "상품")
        val body = objectMapper.writeValueAsString(mapOf("currentPrice" to -1))

        mockMvc
            .perform(
                patch("/api/v1/wishlists/$wishId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .content(body),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
    }

    @Test
    fun `위시를 삭제하면 200 이고 이후 조회에서 제외된다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        val authHeader = "Bearer ${memberToken(userId)}"
        val keptWishId = registerWish(mockMvc, authHeader, "https://shop.example.com/products/1", "남길 상품")
        val deletedWishId = registerWish(mockMvc, authHeader, "https://shop.example.com/products/2", "지울 상품")

        mockMvc
            .perform(
                delete("/api/v1/wishlists/$deletedWishId")
                    .header(HttpHeaders.AUTHORIZATION, authHeader),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))

        mockMvc
            .perform(
                get("/api/v1/wishlists")
                    .header(HttpHeaders.AUTHORIZATION, authHeader),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].wish.id").value(keptWishId))
    }

    @Test
    fun `남의 위시를 삭제하면 403 이 반환된다`() {
        val mockMvc = buildMockMvc()
        val ownerId = UUID.randomUUID()
        val otherId = UUID.randomUUID()
        insertMember(ownerId)
        insertMember(otherId)
        val wishId =
            registerWish(mockMvc, "Bearer ${memberToken(ownerId)}", "https://shop.example.com/products/1", "내 상품")

        mockMvc
            .perform(
                delete("/api/v1/wishlists/$wishId")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(otherId)}"),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
    }

    @Test
    fun `존재하지 않는 위시를 삭제하면 404 가 반환된다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)

        mockMvc
            .perform(
                delete("/api/v1/wishlists/99999999")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(userId)}"),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `OCR 이미지로 등록하면 201 과 함께 item·wish 가 저장되고 sourceUrl 은 null 이다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        stubProductImageExtractor.build = {
            Product(name = "나이키 에어포스", price = 99_000, category = "신발", currency = "KRW")
        }
        val image = MockMultipartFile("image", "product.png", "image/png", byteArrayOf(1, 2, 3))

        mockMvc
            .perform(
                multipart("/api/v1/wishlists/ocr")
                    .file(image)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(userId)}"),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.data.wish.id").isNumber)
            .andExpect(jsonPath("$.data.item.name").value("나이키 에어포스"))
            .andExpect(jsonPath("$.data.item.currentPrice").value(99_000))
            .andExpect(jsonPath("$.data.item.currency").value("KRW"))
            // OCR 항목은 URL·이미지가 없다 (category 는 버려짐, 이미지 저장은 후속)
            .andExpect(jsonPath("$.data.item.sourceUrl").value(nullValue()))
            .andExpect(jsonPath("$.data.item.imageUrl").value(nullValue()))
    }

    @Test
    fun `OCR 등록 후 조회하면 해당 항목이 sourceUrl null 로 함께 반환된다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        val authHeader = "Bearer ${memberToken(userId)}"
        stubProductImageExtractor.build = { Product(name = "에어 조던", price = 119_000, category = null) }
        val image = MockMultipartFile("image", "product.png", "image/png", byteArrayOf(1, 2, 3))

        mockMvc
            .perform(
                multipart("/api/v1/wishlists/ocr").file(image).header(HttpHeaders.AUTHORIZATION, authHeader),
            ).andExpect(status().isCreated)

        mockMvc
            .perform(get("/api/v1/wishlists").header(HttpHeaders.AUTHORIZATION, authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].item.name").value("에어 조던"))
            .andExpect(jsonPath("$.data[0].item.sourceUrl").value(nullValue()))
    }

    @Test
    fun `빈 이미지로 OCR 등록하면 400 BAD_REQUEST 가 반환된다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        val emptyImage = MockMultipartFile("image", "empty.png", "image/png", ByteArray(0))

        mockMvc
            .perform(
                multipart("/api/v1/wishlists/ocr")
                    .file(emptyImage)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(userId)}"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
    }

    @Test
    fun `지원하지 않는 이미지 형식으로 OCR 등록하면 400 BAD_REQUEST 가 반환된다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        val gif = MockMultipartFile("image", "product.gif", "image/gif", byteArrayOf(1, 2, 3))

        mockMvc
            .perform(
                multipart("/api/v1/wishlists/ocr")
                    .file(gif)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(userId)}"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
    }

    @Test
    fun `OCR 등록 중 Gemini 호출이 실패하면 502 BAD_GATEWAY 가 반환된다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        stubProductImageExtractor.build = { throw GeminiApiException.upstreamError(RuntimeException("connection timeout")) }
        val image = MockMultipartFile("image", "product.png", "image/png", byteArrayOf(1, 2, 3))

        mockMvc
            .perform(
                multipart("/api/v1/wishlists/ocr")
                    .file(image)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(userId)}"),
            ).andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.status").value(502))
            .andExpect(jsonPath("$.code").value("BAD_GATEWAY"))
    }
}
