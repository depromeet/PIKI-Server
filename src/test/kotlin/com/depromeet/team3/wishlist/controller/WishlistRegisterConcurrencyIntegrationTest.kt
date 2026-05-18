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
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

// 일반 통합 테스트와 달리 @Transactional 을 사용하지 않는다 — 별도 트랜잭션 동시 진행이
// race 시뮬레이션의 본질이다. 데이터 격리는 매 테스트가 새 UUID userId 를 써서 보장한다.
class WishlistRegisterConcurrencyIntegrationTest : IntegrationTestSupport() {
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

    @Test
    fun `같은 유저와 URL 로 동시 두 요청이 들어오면 한 쪽은 201, 다른 쪽은 409 로 응답된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(
                    webApplicationContext,
                ).apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val url = "https://shop.example.com/products/race-${UUID.randomUUID()}"
        val userId = UUID.randomUUID()
        val userBytes = uuidToBytes(userId)
        val token = "Bearer ${jwtProvider.generateAccessToken(userId, IdentityType.GUEST)}"

        // @Transactional 없는 테스트라 concurrent 요청이 user row 를 볼 수 있도록 먼저 커밋한다.
        jdbcTemplate.update(
            "INSERT INTO user (id, nickname, identity_type, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6))",
            userBytes,
            "테스트유저",
            "GUEST",
        )

        try {
            val body = objectMapper.writeValueAsString(mapOf("url" to url))
            stubExtractor.build = { link -> ProductSnapshot(link = link, name = "race 상품") }

            // 2 단계 래치로 동시 출발을 강제한다. 한 단계 래치만 쓰면 worker 가 await 에 도달하기
            // 전에 main 이 countDown 할 수 있어 사실상 순차 실행으로도 (201, 409) 가 통과해 race
            // 재현 신뢰도가 떨어진다.
            val executor = Executors.newFixedThreadPool(2)
            val workersReady = CountDownLatch(2)
            val start = CountDownLatch(1)
            val futures =
                (0..1).map {
                    executor.submit<Int> {
                        workersReady.countDown()
                        start.await()
                        mockMvc
                            .perform(
                                post("/api/v1/wishlists")
                                    .header(HttpHeaders.AUTHORIZATION, token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body),
                            ).andReturn()
                            .response.status
                    }
                }
            workersReady.await()
            start.countDown()
            val statuses = futures.map { it.get(10, TimeUnit.SECONDS) }.toSet()
            executor.shutdown()

            // 두 경로 모두 의도된 응답:
            // - 한 쪽이 dedup 체크 통과 + persist 성공 → 201
            // - 다른 쪽은 dedup 또는 unique 제약 위반 catch → 409 (500 으로 새지 않는다)
            assertEquals(setOf(201, 409), statuses)
            val wishCount =
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wishes WHERE user_id = ?",
                    Long::class.java,
                    userBytes,
                )
            assertEquals(1L, wishCount)
        } finally {
            jdbcTemplate.update("DELETE FROM wishes WHERE user_id = ?", userBytes)
            jdbcTemplate.update("DELETE FROM user WHERE id = ?", userBytes)
        }
    }
}
