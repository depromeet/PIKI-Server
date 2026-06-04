package com.depromeet.piki.tournament.controller

import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.item.repository.ItemJpaRepository
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.uuidToBytes
import com.depromeet.piki.tournament.domain.TournamentItem
import com.depromeet.piki.tournament.repository.TournamentItemJpaRepository
import com.depromeet.piki.tournament.repository.TournamentJpaRepository
import com.depromeet.piki.tournament.repository.TournamentUserJpaRepository
import com.depromeet.piki.user.domain.IdentityType
import com.depromeet.piki.user.domain.User
import com.depromeet.piki.user.repository.UserJpaRepository
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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

// 일반 통합 테스트와 달리 @Transactional 을 사용하지 않는다 — 별도 트랜잭션 동시 진행이
// race 시뮬레이션의 본질이다. 데이터 격리는 매 테스트가 새 UUID 를 사용하고 finally 에서 직접 정리한다.
class TournamentFromPlayLinkConcurrencyIntegrationTest : IntegrationTestSupport() {
    @Autowired private lateinit var webApplicationContext: WebApplicationContext
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var jwtProvider: JwtProvider
    @Autowired private lateinit var userJpaRepository: UserJpaRepository
    @Autowired private lateinit var itemJpaRepository: ItemJpaRepository
    @Autowired private lateinit var tournamentItemJpaRepository: TournamentItemJpaRepository
    @Autowired private lateinit var tournamentJpaRepository: TournamentJpaRepository
    @Autowired private lateinit var tournamentUserJpaRepository: TournamentUserJpaRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `같은 유저가 from-play-link 를 동시에 두 번 요청하면 하나만 201 나머지는 409 로 처리된다`() {
        val ownerId = UUID.randomUUID()
        val clonerId = UUID.randomUUID()
        userJpaRepository.save(User(id = ownerId, nickname = "race-owner", profileImage = "https://cdn.example.com/o.jpg", identityType = IdentityType.GUEST))
        userJpaRepository.save(User(id = clonerId, nickname = "race-clone", profileImage = "https://cdn.example.com/c.jpg", identityType = IdentityType.GUEST))

        val mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

        val ownerAuth = "Bearer ${jwtProvider.generateAccessToken(ownerId, IdentityType.GUEST)}"
        val clonerAuth = "Bearer ${jwtProvider.generateAccessToken(clonerId, IdentityType.GUEST)}"

        // 소스 토너먼트 준비 (단일 스레드, 직렬)
        val sourceTournamentId = run {
            val createResult = mockMvc.perform(
                post("/api/v1/tournaments")
                    .header(HttpHeaders.AUTHORIZATION, ownerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"동시성테스트토너먼트"}"""),
            ).andReturn()
            val tId = objectMapper.readTree(createResult.response.contentAsString)["data"]["tournamentId"].asLong()

            val item1 = itemJpaRepository.save(Item(name = "race-item1", currentPrice = 10_000, currency = "KRW")).getId()
            val item2 = itemJpaRepository.save(Item(name = "race-item2", currentPrice = 20_000, currency = "KRW")).getId()
            tournamentItemJpaRepository.save(TournamentItem(tournamentId = tId, itemId = item1, userId = ownerId))
            tournamentItemJpaRepository.save(TournamentItem(tournamentId = tId, itemId = item2, userId = ownerId))

            mockMvc.perform(
                post("/api/v1/tournaments/$tId/start")
                    .header(HttpHeaders.AUTHORIZATION, ownerAuth),
            )

            val tiRows = tournamentItemJpaRepository.findAllByTournamentIdAndNotDeleted(tId)
            val ti1 = tiRows[0].getId()
            val ti2 = tiRows[1].getId()
            mockMvc.perform(
                post("/api/v1/tournaments/$tId/matches")
                    .header(HttpHeaders.AUTHORIZATION, ownerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"currentRound":2,"firstTournamentItemId":$ti1,"secondTournamentItemId":$ti2,"selectedTournamentItemId":$ti1}"""),
            )

            mockMvc.perform(
                post("/api/v1/tournaments/$tId/play-link")
                    .header(HttpHeaders.AUTHORIZATION, ownerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"),
            )
            tId
        }

        val status201 = AtomicInteger(0)
        val status409 = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)

        repeat(2) {
            executor.submit {
                ready.countDown()
                start.await()
                try {
                    val res = mockMvc.perform(
                        post("/api/v1/tournaments/$sourceTournamentId/from-play-link")
                            .header(HttpHeaders.AUTHORIZATION, clonerAuth),
                    ).andReturn()
                    when (res.response.status) {
                        201 -> status201.incrementAndGet()
                        409 -> status409.incrementAndGet()
                    }
                } finally {
                    done.countDown()
                }
            }
        }

        ready.await()
        start.countDown()
        done.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertEquals(1, status201.get(), "정확히 하나만 201 이어야 한다")
        assertEquals(1, status409.get(), "정확히 하나만 409 이어야 한다")

        val cloneCount = jdbcTemplate.queryForObject(
            """SELECT COUNT(*) FROM tournaments t
               JOIN tournament_users tu ON tu.tournament_id = t.id
               WHERE t.source_tournament_id = ? AND tu.user_id = ? AND tu.deleted_at IS NULL""",
            Long::class.java, sourceTournamentId, uuidToBytes(clonerId),
        )
        assertEquals(1L, cloneCount, "복제본은 정확히 1개여야 한다")

        // 정리
        jdbcTemplate.update("DELETE FROM tournament_histories WHERE tournament_id = ?", sourceTournamentId)
        jdbcTemplate.update(
            "DELETE FROM tournament_items WHERE tournament_id IN (SELECT id FROM tournaments WHERE source_tournament_id = ? OR id = ?)",
            sourceTournamentId, sourceTournamentId,
        )
        jdbcTemplate.update(
            "DELETE FROM tournament_users WHERE tournament_id IN (SELECT id FROM tournaments WHERE source_tournament_id = ? OR id = ?)",
            sourceTournamentId, sourceTournamentId,
        )
        jdbcTemplate.update("DELETE FROM tournaments WHERE source_tournament_id = ? OR id = ?", sourceTournamentId, sourceTournamentId)
        jdbcTemplate.update("DELETE FROM users WHERE id = ? OR id = ?", uuidToBytes(ownerId), uuidToBytes(clonerId))
    }
}
