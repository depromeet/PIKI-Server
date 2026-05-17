package com.depromeet.team3.tournament.controller

import com.depromeet.team3.support.IntegrationTestSupport
import com.depromeet.team3.tournament.repository.TournamentItemJpaRepository
import com.depromeet.team3.wishlist.domain.Wish
import com.depromeet.team3.wishlist.repository.WishJpaRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Transactional
class TournamentControllerTest : IntegrationTestSupport() {
    @Autowired private lateinit var webApplicationContext: WebApplicationContext

    @Autowired private lateinit var objectMapper: ObjectMapper

    @Autowired private lateinit var wishJpaRepository: WishJpaRepository

    @Autowired private lateinit var tournamentItemJpaRepository: TournamentItemJpaRepository

    private val userId: UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val otherUserId: UUID = UUID.fromString("99999999-8888-7777-6666-555555555555")

    @Test
    fun `POST tournaments 는 201 과 함께 tournamentId 를 반환한다`() {
        val mockMvc = buildMockMvc()

        mockMvc
            .perform(
                post("/api/v1/tournaments")
                    .header("X-User-Id", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"테스트 토너먼트"}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.code").value("CREATED"))
            .andExpect(jsonPath("$.data.tournamentId").isNumber)
    }

    @Test
    fun `POST tournaments-id-items 는 소유한 위시를 추가하면 200 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        val wishIds = saveWishes(userId, 100L, 200L)

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/items")
                    .header("X-User-Id", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"itemIds":${wishIds.joinToString(",", "[", "]")}}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))
    }

    @Test
    fun `POST tournaments-id-items 에서 소유하지 않은 위시이면 403 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        val otherWishIds = saveWishes(otherUserId, 100L, 200L)

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/items")
                    .header("X-User-Id", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"itemIds":${otherWishIds.joinToString(",", "[", "]")}}"""),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
    }

    @Test
    fun `POST tournaments-id-start 는 아이템이 있는 PENDING 토너먼트를 시작하고 200 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        addItemsToTournament(mockMvc, tournamentId, userId, 100L, 200L)

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/start")
                    .header("X-User-Id", userId),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))
    }

    @Test
    fun `POST tournaments-id-start 에서 소유자가 아니면 403 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        addItemsToTournament(mockMvc, tournamentId, userId, 100L, 200L)

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/start")
                    .header("X-User-Id", otherUserId),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
    }

    @Test
    fun `POST tournaments-id-start 에서 아이템이 없으면 400 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/start")
                    .header("X-User-Id", userId),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
    }

    @Test
    fun `POST tournaments-id-matches 는 IN_PROGRESS 토너먼트에 매치를 기록하고 200 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val (tournamentId, item1Id, item2Id) = startTournamentWith2Items(mockMvc)

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/matches")
                    .header("X-User-Id", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"currentRound":2,"firstTournamentItemId":$item1Id,"secondTournamentItemId":$item2Id,"selectedTournamentItemId":$item1Id}""",
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))
    }

    @Test
    fun `POST tournaments-id-matches 에서 이미 COMPLETED 인 토너먼트이면 409 를 반환한다`() {
        val mockMvc = buildMockMvc()
        val (tournamentId, item1Id, item2Id) = startTournamentWith2Items(mockMvc)
        val matchBody =
            """{"currentRound":2,"firstTournamentItemId":$item1Id,"secondTournamentItemId":$item2Id,"selectedTournamentItemId":$item1Id}"""

        mockMvc.perform(
            post("/api/v1/tournaments/$tournamentId/matches")
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(matchBody),
        )

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/matches")
                    .header("X-User-Id", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(matchBody),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.status").value(409))
    }

    @Test
    fun `POST tournaments-id-matches 에서 참가자가 아니면 403 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val (tournamentId, item1Id, item2Id) = startTournamentWith2Items(mockMvc)

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/matches")
                    .header("X-User-Id", otherUserId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"currentRound":2,"firstTournamentItemId":$item1Id,"secondTournamentItemId":$item2Id,"selectedTournamentItemId":$item1Id}""",
                    ),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
    }

    @Test
    fun `GET tournaments-id 는 토너먼트 정보와 히스토리를 반환한다`() {
        val mockMvc = buildMockMvc()
        val (tournamentId, item1Id, item2Id) = startTournamentWith2Items(mockMvc)

        mockMvc.perform(
            post("/api/v1/tournaments/$tournamentId/matches")
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"currentRound":2,"firstTournamentItemId":$item1Id,"secondTournamentItemId":$item2Id,"selectedTournamentItemId":$item1Id}""",
                ),
        )

        mockMvc
            .perform(
                get("/api/v1/tournaments/$tournamentId")
                    .header("X-User-Id", userId),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.tournamentId").value(tournamentId))
            .andExpect(jsonPath("$.data.initialRound").value(2))
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.history[0].selectedTournamentItemId").value(item1Id))
    }

    @Test
    fun `GET tournaments-id 에서 참가자가 아니면 403 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val (tournamentId) = startTournamentWith2Items(mockMvc)

        mockMvc
            .perform(
                get("/api/v1/tournaments/$tournamentId")
                    .header("X-User-Id", otherUserId),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
    }

    @Test
    fun `GET tournaments-id 에서 존재하지 않는 tournamentId 이면 404 를 반환한다`() {
        val mockMvc = buildMockMvc()

        mockMvc
            .perform(
                get("/api/v1/tournaments/999999")
                    .header("X-User-Id", userId),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
    }

    private fun buildMockMvc(): MockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()

    private fun createTournament(
        mockMvc: MockMvc,
        name: String = "테스트 토너먼트",
    ): Long {
        val result =
            mockMvc
                .perform(
                    post("/api/v1/tournaments")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"$name"}"""),
                ).andReturn()
        return objectMapper.readTree(result.response.contentAsString)["data"]["tournamentId"].asLong()
    }

    private fun saveWishes(
        owner: UUID,
        vararg itemIds: Long,
    ): List<Long> = itemIds.map { itemId -> wishJpaRepository.save(Wish(userId = owner, itemId = itemId)).getId() }

    private fun addItemsToTournament(
        mockMvc: MockMvc,
        tournamentId: Long,
        owner: UUID,
        vararg itemIds: Long,
    ) {
        val wishIds = saveWishes(owner, *itemIds)
        mockMvc.perform(
            post("/api/v1/tournaments/$tournamentId/items")
                .header("X-User-Id", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"itemIds":${wishIds.joinToString(",", "[", "]")}}"""),
        )
    }

    private data class TournamentStart(
        val tournamentId: Long,
        val item1Id: Long,
        val item2Id: Long,
    )

    private fun startTournamentWith2Items(mockMvc: MockMvc): TournamentStart {
        val tournamentId = createTournament(mockMvc)
        addItemsToTournament(mockMvc, tournamentId, userId, 100L, 200L)
        mockMvc.perform(
            post("/api/v1/tournaments/$tournamentId/start")
                .header("X-User-Id", userId),
        )
        val items = tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId)
        return TournamentStart(
            tournamentId = tournamentId,
            item1Id = items[0].getId(),
            item2Id = items[1].getId(),
        )
    }
}
