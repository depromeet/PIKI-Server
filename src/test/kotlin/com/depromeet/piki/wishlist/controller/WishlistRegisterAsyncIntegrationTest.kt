package com.depromeet.piki.wishlist.controller

import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.image.domain.BoundingBox
import com.depromeet.piki.image.service.ImageExtraction
import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.item.domain.ItemSnapshot
import com.depromeet.piki.item.domain.ItemStatus
import com.depromeet.piki.item.repository.ItemRepository
import com.depromeet.piki.item.repository.ItemSnapshotRepository
import com.depromeet.piki.item.service.ItemParsingService
import com.depromeet.piki.product.domain.ProductLink
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
import java.time.LocalDateTime
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
    private lateinit var itemSnapshotRepository: ItemSnapshotRepository

    @Autowired
    private lateinit var itemParsingService: ItemParsingService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Test
    fun `등록하면 추출을 기다리지 않고 PENDING 상태로 201 이 즉시 반환된다`() {
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
                .andExpect(jsonPath("$.data.wish.id").isNumber)
                .andExpect(jsonPath("$.data.item.status").value("PENDING"))
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
                latestSnapshot(itemId)?.status == ItemStatus.READY
            }

            // 표시값·상태는 활성 snapshot 이 보유한다(4a) — item 은 정체성(link)만 든다.
            val snapshot = latestSnapshot(itemId) ?: error("item $itemId 의 snapshot 이 없다")
            assertEquals("나이키 에어포스", snapshot.name)
            assertEquals(99_000, snapshot.currentPrice)
            assertEquals("KRW", snapshot.currency)
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
                latestSnapshot(itemId)?.status == ItemStatus.FAILED
            }

            val snapshot = latestSnapshot(itemId) ?: error("item $itemId 의 snapshot 이 없다")
            assertEquals(ItemStatus.FAILED, snapshot.status)
            // 실패 항목은 추출 결과가 비어 있다.
            assertNull(snapshot.name)
        } finally {
            cleanup(userId)
        }
    }

    @Test
    fun `추출은 됐으나 이름이 비어 있으면 READY 부적격으로 item 이 FAILED 로 전이한다`() {
        val mockMvc = buildMockMvc()
        val userId = UUID.randomUUID()
        insertMember(userId)
        try {
            // isProductPage=true 라도 이름을 못 뽑으면 name 이 비어 온다. READY 불변식(name 필수)에 걸려
            // markReady 가 거부하고, 워커가 이를 받아 PROCESSING 방치 대신 FAILED 로 떨어뜨린다.
            stubProductLinkExtractor.build = { ProductSnapshot(link = it, currentPrice = 99_000) }
            val itemId = registerAndGetItemId(mockMvc, userId, "https://shop.example.com/products/no-name")

            await().atMost(Duration.ofSeconds(5)).until {
                latestSnapshot(itemId)?.status == ItemStatus.FAILED
            }

            val snapshot = latestSnapshot(itemId) ?: error("item $itemId 의 snapshot 이 없다")
            assertEquals(ItemStatus.FAILED, snapshot.status)
            assertNull(snapshot.name)
        } finally {
            cleanup(userId)
        }
    }

    @Test
    fun `같은 URL 을 두 번 등록해도 dedup 없이 둘 다 201 PENDING 으로 별개 등록된다`() {
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
                    .andExpect(jsonPath("$.data.item.status").value("PENDING"))
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
                latestSnapshot(itemId)?.status == ItemStatus.READY
            }
            val snapshot = latestSnapshot(itemId) ?: error("item $itemId 의 snapshot 이 없다")
            assertEquals("나이키 에어포스", snapshot.name)
            assertEquals(99_000, snapshot.currentPrice)
            assertEquals("KRW", snapshot.currency)
            // 이미지 등록은 link(원본 URL)가 없다 — link 는 정체성이라 item 에서 읽는다.
            val item = itemRepository.findById(itemId) ?: error("item $itemId 가 없다")
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
                latestSnapshot(itemId)?.status == ItemStatus.READY
            }
            val snapshot = latestSnapshot(itemId) ?: error("item $itemId 의 snapshot 이 없다")
            assertEquals(true, snapshot.imageUrl?.startsWith(StubImageStorage.BASE_URL))
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
                latestSnapshot(itemId)?.status == ItemStatus.FAILED
            }
            val snapshot = latestSnapshot(itemId) ?: error("item $itemId 의 snapshot 이 없다")
            assertEquals(ItemStatus.FAILED, snapshot.status)
            assertNull(snapshot.name)
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
                itemIds.all { latestSnapshot(it)?.status == ItemStatus.READY }
            }
        } finally {
            cleanup(userId)
        }
    }

    @Test
    fun `updated_at 이 오래된 PROCESSING snapshot 을 recover 가 FAILED 로 정리한다`() {
        // 워커가 죽어 PROCESSING 에 갇힌 상황 — 디스패처는 PENDING 만 집으므로 이 행은 recover 가 책임진다.
        val item = itemRepository.save(Item(ProductLink.parse("https://shop.example.com/products/stale")))
        val snapshot = itemSnapshotRepository.save(ItemSnapshot.processing(item.getId()))
        val itemId = item.getId()
        try {
            // 이 snapshot 의 updated_at 만 과거로 밀어 stale 로 만들고, 현실적 threshold(now-5분)로 recover 를 부른다.
            // future threshold 로 부르면 다른 테스트가 막 만든 PROCESSING(updated_at 최근)까지 쓸어버리므로(공유 컨텍스트),
            // updated_at 기반 stale 판정을 실제로 검증하면서 자기 snapshot 만 대상이 되도록 격리한다.
            jdbcTemplate.update(
                "UPDATE item_snapshots SET updated_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(10),
                snapshot.getId(),
            )

            itemParsingService.recoverStaleProcessing(LocalDateTime.now().minusMinutes(5), 100)

            assertEquals(ItemStatus.FAILED, itemSnapshotRepository.findLatestByItemId(itemId)?.status)
        } finally {
            jdbcTemplate.update("DELETE FROM item_snapshots WHERE item_id = ?", itemId)
            jdbcTemplate.update("DELETE FROM items WHERE id = ?", itemId)
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

    // 표시값·상태는 item 의 활성(최신) snapshot 이 보유한다(4a). 폴링·단언이 이 snapshot 을 읽는다.
    private fun latestSnapshot(itemId: Long): ItemSnapshot? = itemSnapshotRepository.findLatestByItemId(itemId)

    // @Transactional 자동 롤백이 없으므로 이 테스트가 만든 user·wish·item·snapshot 을 직접 정리한다.
    private fun cleanup(userId: UUID) {
        val itemIds =
            jdbcTemplate.queryForList(
                "SELECT item_id FROM wishes WHERE user_id = ?",
                Long::class.java,
                uuidToBytes(userId),
            )
        jdbcTemplate.update("DELETE FROM wishes WHERE user_id = ?", uuidToBytes(userId))
        itemIds.takeIf { it.isNotEmpty() }?.let {
            jdbcTemplate.update("DELETE FROM item_snapshots WHERE item_id IN (${it.joinToString(",")})")
            jdbcTemplate.update("DELETE FROM items WHERE id IN (${it.joinToString(",")})")
        }
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", uuidToBytes(userId))
    }
}
