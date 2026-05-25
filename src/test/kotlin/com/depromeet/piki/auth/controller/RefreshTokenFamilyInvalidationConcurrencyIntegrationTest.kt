package com.depromeet.piki.auth.controller

import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.auth.infrastructure.redis.RefreshTokenStore
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.uuidToBytes
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

// 일반 통합 테스트와 달리 @Transactional 을 사용하지 않는다 — 별도 트랜잭션 동시 진행이
// race 시뮬레이션의 본질이다. 데이터 격리는 매 테스트가 새 게스트 생성으로 보장한다.
class RefreshTokenFamilyInvalidationConcurrencyIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var refreshTokenStore: RefreshTokenStore

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `같은 refreshToken 으로 두 요청이 동시에 들어오면 한 쪽 200, 다른 쪽 401 로 family invalidation 트리거된다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        val guestResult =
            mockMvc
                .perform(post("/api/v1/auth/guest").contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val guestJson = objectMapper.readTree(guestResult.response.contentAsString)
        val accessToken = guestJson.at("/data/accessToken").asText()
        val r1 = guestJson.at("/data/refreshToken").asText()
        val userId = jwtProvider.parseAccessToken(accessToken)?.userId ?: error("accessToken 파싱 실패")

        try {
            val body = objectMapper.writeValueAsString(mapOf("refreshToken" to r1))

            // 2 단계 래치로 동시 출발 강제 — 한 단계 래치만 쓰면 worker 가 await 도달 전에
            // main 이 countDown 할 수 있어 사실상 순차 실행이 돼 race 재현 신뢰도가 떨어진다.
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
                                post("/api/v1/auth/token/refresh")
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

            // 한 쪽이 R1 매치 통과 + R2 발급 (200), 다른 쪽은 mismatch 로 family invalidation
            // 트리거 (401). 정상 사용자라면 두 요청이 동시에 가지 않으므로 production 에선
            // race 영향이 거의 없다.
            assertEquals(setOf(200, 401), statuses)
        } finally {
            refreshTokenStore.delete(userId)
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", uuidToBytes(userId))
        }
    }
}
