package com.depromeet.piki.wishlist.controller

import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.image.domain.BoundingBox
import com.depromeet.piki.image.service.ImageExtraction
import com.depromeet.piki.item.domain.ItemStatus
import com.depromeet.piki.item.repository.ItemRepository
import com.depromeet.piki.product.service.ProductSnapshot
import com.depromeet.piki.product.service.ProductSnapshotException
import com.depromeet.piki.product.service.gemini.GeminiApiException
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.StubImageStorage
import com.depromeet.piki.support.StubProductImageExtractor
import com.depromeet.piki.support.StubProductLinkExtractor
import com.depromeet.piki.support.uuidToBytes
import com.depromeet.piki.user.domain.IdentityType
import org.awaitility.Awaitility.await
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNull

// 등록은 비동기(@Async)다. @Transactional 자동 롤백 패턴으로는 워커(별도 스레드·새 트랜잭션)가
// 미커밋 데이터를 못 보므로, 여기서는 @Transactional 없이 실제 커밋하고 Awaitility 로 상태 전이를 기다린다.
// (CLAUDE.md '동시성·시간 의존 통합 테스트' 별도 분류.) 자기가 만든 행은 격리 userId 로 구분해 메서드 끝에서 정리한다.
class WishlistRegisterAsyncIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var stubProductLinkExtractor: StubProductLinkExtractor

    @Autowired
    private lateinit var stubProductImageExtractor: StubProductImageExtractor

    @Autowired
    private lateinit var itemRepository: ItemRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Test
    fun `등록하면 추출을 기다리지 않고 PROCESSING 상태로 201 이 즉시 반환된다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        try {
            stubProductLinkExtractor.build = { ProductSnapshot(link = it, name = "나이키 에어포스", currentPrice = 99_000) }
            val body = objectMapper.writeValueAsString(mapOf("url" to "https://shop.example.com/products/42"))

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
                .andExpect(jsonPath("$.data.item.status").value("PROCESSING"))
                // 파싱 전이라 추출 결과는 아직 비어 있고, 입력으로 받은 sourceUrl 만 채워진다.
                .andExpect(jsonPath("$.data.item.name").value(nullValue()))
                .andExpect(jsonPath("$.data.item.currentPrice").value(nullValue()))
                .andExpect(jsonPath("$.data.item.sourceUrl").value("https://shop.example.com/products/42"))
        } finally {
            cleanup(userId)
        }
    }

    @Test
    fun `등록 후 파싱이 성공하면 item 이 READY 로 전이하며 추출 결과가 채워진다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        try {
            stubProductLinkExtractor.build = {
                ProductSnapshot(link = it, name = "나이키 에어포스", currentPrice = 99_000, currency = "KRW")
            }
            val itemId = registerAndGetItemId(mockMvc, userId, "https://shop.example.com/products/42")

            await().atMost(Duration.ofSeconds(5)).until {
                itemRepository.findById(itemId)?.status == ItemStatus.READY
            }

            val item = itemRepository.findById(itemId) ?: error("item $itemId 가 없다")
            assertEquals("나이키 에어포스", item.name)
            assertEquals(99_000, item.currentPrice)
            assertEquals("KRW", item.currency)
        } finally {
            cleanup(userId)
        }
    }

    @Test
    fun `등록 후 상품 페이지가 아니라고 판정되면 item 이 FAILED 로 전이한다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        try {
            // 파싱 결과 실패는 동기 400 이 아니라 FAILED 상태로 남는다 (등록 응답은 이미 201 로 끝났으므로).
            stubProductLinkExtractor.build = { throw ProductSnapshotException.notProductPage() }
            val itemId = registerAndGetItemId(mockMvc, userId, "https://shop.example.com/products/not-a-product")

            await().atMost(Duration.ofSeconds(5)).until {
                itemRepository.findById(itemId)?.status == ItemStatus.FAILED
            }

            val item = itemRepository.findById(itemId) ?: error("item $itemId 가 없다")
            assertEquals(ItemStatus.FAILED, item.status)
            // 실패 항목은 추출 결과가 비어 있다.
            assertNull(item.name)
        } finally {
            cleanup(userId)
        }
    }

    @Test
    fun `같은 URL 을 두 번 등록해도 dedup 없이 둘 다 201 PROCESSING 으로 별개 등록된다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        try {
            stubProductLinkExtractor.build = { ProductSnapshot(link = it, name = "기본 상품") }
            val body = objectMapper.writeValueAsString(mapOf("url" to "https://shop.example.com/products/42"))
            val auth = "Bearer ${memberToken(userId)}"

            repeat(2) {
                mockMvc
                    .perform(
                        post("/api/v1/wishlists")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .content(body),
                    ).andExpect(status().isCreated)
                    .andExpect(jsonPath("$.data.item.status").value("PROCESSING"))
            }

            // dedup 없는 multi-record 모델 — 같은 URL 이라도 별개 wish 2건이 쌓인다 (persist 는 동기라 즉시 반영).
            val wishCount =
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wishes WHERE user_id = ?",
                    Int::class.java,
                    uuidToBytes(userId),
                )
            assertEquals(2, wishCount)
        } finally {
            cleanup(userId)
        }
    }

    @Test
    fun `이미지로 등록하면 PROCESSING 으로 201 즉시 반환 후 파싱 성공 시 READY 로 전이한다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        try {
            stubProductImageExtractor.build = {
                ImageExtraction(
                    snapshot = ProductSnapshot(link = null, name = "나이키 에어포스", currentPrice = 99_000, currency = "KRW"),
                    boundingBox = null,
                )
            }
            val image = MockMultipartFile("images", "p.png", "image/png", byteArrayOf(1, 2, 3))
            val itemId = registerImageAndGetItemId(mockMvc, userId, image)

            await().atMost(Duration.ofSeconds(5)).until {
                itemRepository.findById(itemId)?.status == ItemStatus.READY
            }
            val item = itemRepository.findById(itemId) ?: error("item $itemId 가 없다")
            assertEquals("나이키 에어포스", item.name)
            assertEquals(99_000, item.currentPrice)
            assertEquals("KRW", item.currency)
            // 이미지 등록은 link(원본 URL)가 없다.
            assertNull(item.link)
        } finally {
            cleanup(userId)
        }
    }

    @Test
    fun `이미지에 bbox 가 있으면 비동기 파싱이 크롭 이미지를 올려 imageUrl 이 채워진다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        try {
            stubProductImageExtractor.build = {
                ImageExtraction(
                    snapshot = ProductSnapshot(link = null, name = "나이키 에어포스", currentPrice = 99_000),
                    boundingBox = BoundingBox(yMin = 100, xMin = 100, yMax = 500, xMax = 500),
                )
            }
            // 크롭이 동작하려면 실제 디코딩 가능한 PNG 여야 한다 (ImageCropper 는 실제 빈, 업로드만 stub).
            val pngBytes =
                ByteArrayOutputStream().use { out ->
                    ImageIO.write(BufferedImage(800, 800, BufferedImage.TYPE_INT_RGB), "png", out)
                    out.toByteArray()
                }
            val image = MockMultipartFile("images", "p.png", "image/png", pngBytes)
            val itemId = registerImageAndGetItemId(mockMvc, userId, image)

            await().atMost(Duration.ofSeconds(5)).until {
                itemRepository.findById(itemId)?.status == ItemStatus.READY
            }
            val item = itemRepository.findById(itemId) ?: error("item $itemId 가 없다")
            assertEquals(true, item.imageUrl?.startsWith(StubImageStorage.BASE_URL))
        } finally {
            cleanup(userId)
        }
    }

    @Test
    fun `이미지 파싱이 실패하면 item 이 FAILED 로 전이한다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        try {
            stubProductImageExtractor.build = {
                throw GeminiApiException.upstreamError(RuntimeException("connection timeout"))
            }
            val image = MockMultipartFile("images", "p.png", "image/png", byteArrayOf(1, 2, 3))
            val itemId = registerImageAndGetItemId(mockMvc, userId, image)

            await().atMost(Duration.ofSeconds(5)).until {
                itemRepository.findById(itemId)?.status == ItemStatus.FAILED
            }
            val item = itemRepository.findById(itemId) ?: error("item $itemId 가 없다")
            assertEquals(ItemStatus.FAILED, item.status)
            assertNull(item.name)
        } finally {
            cleanup(userId)
        }
    }

    @Test
    fun `이미지 5개를 등록하면 모두 PROCESSING 으로 반환되고 각각 READY 로 전이한다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        try {
            stubProductImageExtractor.build = {
                ImageExtraction(
                    snapshot = ProductSnapshot(link = null, name = "상품", currentPrice = 1_000),
                    boundingBox = null,
                )
            }
            val request = multipart("/api/v1/wishlists/images")
            (1..5).forEach { i ->
                request.file(MockMultipartFile("images", "p$i.png", "image/png", byteArrayOf(i.toByte())))
            }
            request.header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(userId)}")
            val response =
                mockMvc
                    .perform(request)
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.data.length()").value(5))
                    // 등록 직후 응답은 모두 PROCESSING 이어야 한다 — 서버가 즉시 READY 를 내리는 회귀를 잡는다.
                    .andExpect(jsonPath("$.data[0].item.status").value("PROCESSING"))
                    .andExpect(jsonPath("$.data[4].item.status").value("PROCESSING"))
                    .andReturn()
                    .response
                    .getContentAsString(Charsets.UTF_8)
            val dataNode = objectMapper.readTree(response).path("data")
            val itemIds =
                (0 until dataNode.size()).map { i ->
                    dataNode
                        .path(i)
                        .path("item")
                        .path("id")
                        .asLong()
                }

            await().atMost(Duration.ofSeconds(5)).until {
                itemIds.all { itemRepository.findById(it)?.status == ItemStatus.READY }
            }
        } finally {
            cleanup(userId)
        }
    }

    private fun registerAndGetItemId(
        mockMvc: MockMvc,
        userId: UUID,
        url: String,
    ): Long {
        val body = objectMapper.writeValueAsString(mapOf("url" to url))
        val response =
            mockMvc
                .perform(
                    post("/api/v1/wishlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(userId)}")
                        .content(body),
                ).andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return objectMapper
            .readTree(response)
            .path("data")
            .path("item")
            .path("id")
            .asLong()
    }

    private fun registerImageAndGetItemId(
        mockMvc: MockMvc,
        userId: UUID,
        image: MockMultipartFile,
    ): Long {
        val response =
            mockMvc
                .perform(
                    multipart("/api/v1/wishlists/images")
                        .file(image)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(userId)}"),
                ).andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return objectMapper
            .readTree(response)
            .path("data")
            .path(0)
            .path("item")
            .path("id")
            .asLong()
    }

    private fun buildMockMvc(): MockMvc =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    private fun insertMember(userId: UUID) {
        jdbcTemplate.update(
            "INSERT INTO users (id, nickname, identity_type, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6))",
            uuidToBytes(userId),
            userId.toString().take(10),
            "MEMBER",
        )
    }

    private fun memberToken(userId: UUID): String = jwtProvider.generateAccessToken(userId, IdentityType.MEMBER)

    // @Transactional 자동 롤백이 없으므로 이 테스트가 만든 user·wish·item 을 직접 정리한다.
    private fun cleanup(userId: UUID) {
        val itemIds =
            jdbcTemplate.queryForList(
                "SELECT item_id FROM wishes WHERE user_id = ?",
                Long::class.java,
                uuidToBytes(userId),
            )
        jdbcTemplate.update("DELETE FROM wishes WHERE user_id = ?", uuidToBytes(userId))
        itemIds.takeIf { it.isNotEmpty() }?.let {
            jdbcTemplate.update("DELETE FROM items WHERE id IN (${it.joinToString(",")})")
        }
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", uuidToBytes(userId))
    }
}
