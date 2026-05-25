package com.depromeet.piki.wishlist.controller

import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.ocr.domain.BoundingBox
import com.depromeet.piki.ocr.service.OcrExtraction
import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.product.service.ProductSnapshot
import com.depromeet.piki.product.service.gemini.GeminiApiException
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.StubImageStorage
import com.depromeet.piki.support.StubProductImageExtractor
import com.depromeet.piki.support.uuidToBytes
import com.depromeet.piki.user.domain.IdentityType
import com.depromeet.piki.wishlist.service.WishPersistenceService
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.startsWith
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
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

// 조회·수정·삭제 contract 검증. 이 시나리오들의 본질은 "완성된 위시가 있을 때의 동작"이라
// 등록(비동기) 경로를 거치지 않고 seedReadyWish 로 READY 상태를 시딩한다.
// 등록 PROCESSING 응답·파싱 전이는 WishlistRegisterAsyncIntegrationTest 가 검증한다.
@Transactional
class WishlistControllerIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var stubProductImageExtractor: StubProductImageExtractor

    @Autowired
    private lateinit var wishPersistenceService: WishPersistenceService

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

    // 조회·수정·삭제 시나리오의 데이터 시딩. 등록 API(비동기)를 거치지 않고 영속화 빈으로 READY item+wish 를
    // 바로 만든다 — 이 테스트들의 관심사는 "완성된 위시가 있을 때"이지 등록 흐름이 아니기 때문.
    private fun seedReadyWish(
        userId: UUID,
        url: String,
        name: String,
        currentPrice: Int? = null,
        currency: String? = null,
        imageUrl: String? = null,
    ): Long =
        wishPersistenceService
            .persist(
                userId,
                Item.from(
                    ProductSnapshot(
                        link = ProductLink.parse(url),
                        name = name,
                        currentPrice = currentPrice,
                        currency = currency,
                        imageUrl = imageUrl,
                    ),
                ),
            ).wish
            .getId()

    @Test
    fun `url 이 빈 문자열이면 400 BAD_REQUEST 가 반환된다`() {
        val mockMvc = buildMockMvc()
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
        // URL 형식 위반은 등록 전(ProductLink.parse) 동기로 거르므로 백그라운드 파싱에 닿지 않는다.
        val mockMvc = buildMockMvc()
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
    fun `본인 위시만 최신 등록순으로 반환하고 다른 유저 wish 는 섞이지 않으며 status 가 함께 내려간다`() {
        val mockMvc = buildMockMvc()
        val ownerId = UUID.randomUUID()
        val otherId = UUID.randomUUID()
        insertMember(ownerId)
        insertMember(otherId)
        seedReadyWish(ownerId, "https://shop.example.com/products/1", "첫 상품")
        val secondWishId = seedReadyWish(ownerId, "https://shop.example.com/products/2", "둘째 상품")
        seedReadyWish(otherId, "https://shop.example.com/products/3", "남의 상품")

        mockMvc
            .perform(
                get("/api/v1/wishlists")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(ownerId)}"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(2))
            // 최신 등록(둘째)이 맨 앞 (id desc)
            .andExpect(jsonPath("$.data[0].wish.id").value(secondWishId))
            .andExpect(jsonPath("$.data[0].item.name").value("둘째 상품"))
            .andExpect(jsonPath("$.data[0].item.status").value("READY"))
            .andExpect(jsonPath("$.data[1].item.name").value("첫 상품"))
            .andExpect(jsonPath("$.pageResponse.hasNext").value(false))
    }

    @Test
    fun `size 보다 많으면 hasNext 와 nextCursor 를 주고 그 cursor 로 다음 페이지를 잇는다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        val firstWishId = seedReadyWish(userId, "https://shop.example.com/products/1", "상품1")
        val secondWishId = seedReadyWish(userId, "https://shop.example.com/products/2", "상품2")
        seedReadyWish(userId, "https://shop.example.com/products/3", "상품3")
        val authHeader = "Bearer ${memberToken(userId)}"

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
        val wishId = seedReadyWish(userId, "https://shop.example.com/products/1", "잘못 읽힌 이름")
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
        val wishId =
            seedReadyWish(
                userId,
                "https://shop.example.com/products/1",
                name = "원래 이름",
                currentPrice = 50_000,
                currency = "KRW",
                imageUrl = "https://cdn.example.com/orig.jpg",
            )

        mockMvc
            .perform(
                patch("/api/v1/wishlists/$wishId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .content(objectMapper.writeValueAsString(mapOf("name" to "새 이름"))),
            ).andExpect(status().isOk)
            // name 만 갱신, 나머지는 시딩 시점 값 유지
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
        val wishId = seedReadyWish(ownerId, "https://shop.example.com/products/1", "내 상품")
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
        val wishId = seedReadyWish(userId, "https://shop.example.com/products/1", "상품")
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
        val keptWishId = seedReadyWish(userId, "https://shop.example.com/products/1", "남길 상품")
        val deletedWishId = seedReadyWish(userId, "https://shop.example.com/products/2", "지울 상품")

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
        val wishId = seedReadyWish(ownerId, "https://shop.example.com/products/1", "내 상품")

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
            OcrExtraction(
                snapshot = ProductSnapshot(link = null, name = "나이키 에어포스", currentPrice = 99_000, currency = "KRW"),
                boundingBox = null,
            )
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
            // OCR 은 동기 완성이라 status 는 READY.
            .andExpect(jsonPath("$.data.item.status").value("READY"))
            // bbox 가 없으면 크롭을 건너뛰어 imageUrl 은 null. sourceUrl 도 OCR 이라 null.
            .andExpect(jsonPath("$.data.item.sourceUrl").value(nullValue()))
            .andExpect(jsonPath("$.data.item.imageUrl").value(nullValue()))
    }

    @Test
    fun `OCR 이미지에 bbox 가 있으면 크롭 이미지를 업로드해 imageUrl 이 채워진다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        stubProductImageExtractor.build = {
            OcrExtraction(
                snapshot = ProductSnapshot(link = null, name = "나이키 에어포스", currentPrice = 99_000, currency = "KRW"),
                boundingBox = BoundingBox(yMin = 100, xMin = 100, yMax = 500, xMax = 500),
            )
        }
        // 크롭이 동작하려면 실제 디코딩 가능한 PNG 여야 한다 (ImageCropper 는 실제 빈, 업로드만 stub).
        val pngBytes =
            ByteArrayOutputStream().use { out ->
                ImageIO.write(BufferedImage(800, 800, BufferedImage.TYPE_INT_RGB), "png", out)
                out.toByteArray()
            }
        val image = MockMultipartFile("image", "product.png", "image/png", pngBytes)

        mockMvc
            .perform(
                multipart("/api/v1/wishlists/ocr")
                    .file(image)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(userId)}"),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.item.imageUrl").value(startsWith(StubImageStorage.BASE_URL)))
    }

    @Test
    fun `OCR 등록 후 조회하면 해당 항목이 sourceUrl null 로 함께 반환된다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        val authHeader = "Bearer ${memberToken(userId)}"
        stubProductImageExtractor.build = {
            OcrExtraction(
                snapshot = ProductSnapshot(link = null, name = "에어 조던", currentPrice = 119_000),
                boundingBox = null,
            )
        }
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
