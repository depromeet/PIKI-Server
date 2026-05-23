package com.depromeet.piki.tournament.controller

import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.tournament.domain.TournamentItem
import com.depromeet.piki.tournament.domain.TournamentUser
import com.depromeet.piki.tournament.repository.TournamentItemJpaRepository
import com.depromeet.piki.tournament.repository.TournamentUserJpaRepository
import com.depromeet.piki.user.domain.IdentityType
import com.depromeet.piki.user.domain.User
import com.depromeet.piki.user.repository.UserJpaRepository
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
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
class TournamentControllerTest : IntegrationTestSupport() {
    @Autowired private lateinit var webApplicationContext: WebApplicationContext

    @Autowired private lateinit var objectMapper: ObjectMapper

    @Autowired private lateinit var tournamentItemJpaRepository: TournamentItemJpaRepository

    @Autowired private lateinit var tournamentUserJpaRepository: TournamentUserJpaRepository

    @Autowired private lateinit var userJpaRepository: UserJpaRepository

    @Autowired private lateinit var jwtProvider: JwtProvider

    private val userId: UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val otherUserId: UUID = UUID.fromString("99999999-8888-7777-6666-555555555555")
    private val userProfileImage = "https://cdn.example.com/profiles/user.jpg"

    private fun authHeader(userId: UUID): String =
        "Bearer ${jwtProvider.generateAccessToken(userId, IdentityType.MEMBER)}"

    @Test
    fun `POST tournaments 는 201 과 함께 tournamentId 를 반환한다`() {
        val mockMvc = buildMockMvc()

        mockMvc
            .perform(
                post("/api/v1/tournaments")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"테스트 토너먼트"}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.code").value("CREATED"))
            .andExpect(jsonPath("$.data.tournamentId").isNumber)
    }

    @Test
    fun `POST tournaments-id-items 는 참여자이면 200 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/items")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"itemIds":[100,200]}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))
    }

    @Test
    fun `POST tournaments-id-items 는 아이템 1개도 추가할 수 있다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/items")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"itemIds":[100]}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))
    }

    @Test
    fun `POST tournaments-id-items 에서 빈 itemIds 는 400 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/items")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"itemIds":[]}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
    }

    @Test
    fun `POST tournaments-id-items 에서 토너먼트 참여자가 아니면 403 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/items")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(otherUserId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"itemIds":[100,200]}"""),
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
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
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
                    .header(HttpHeaders.AUTHORIZATION, authHeader(otherUserId)),
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
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
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
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
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
                .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(matchBody),
        )

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/matches")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
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
                    .header(HttpHeaders.AUTHORIZATION, authHeader(otherUserId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"currentRound":2,"firstTournamentItemId":$item1Id,"secondTournamentItemId":$item2Id,"selectedTournamentItemId":$item1Id}""",
                    ),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
    }

    @Test
    fun `GET tournaments 는 내 토너먼트 목록을 200 과 함께 반환하고 참여자 프로필 이미지를 포함한다`() {
        val mockMvc = buildMockMvc()
        saveUser(userId, userProfileImage)
        createTournament(mockMvc, "토너먼트A")
        createTournament(mockMvc, "토너먼트B")

        mockMvc
            .perform(
                get("/api/v1/tournaments")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].tournamentId").isNumber)
            .andExpect(jsonPath("$.data[0].name").isString)
            .andExpect(jsonPath("$.data[0].status").isString)
            .andExpect(jsonPath("$.data[0].createdAt").isString)
            .andExpect(jsonPath("$.data[0].participantProfileImages").isArray)
            .andExpect(jsonPath("$.data[0].participantProfileImages[0]").value(userProfileImage))
    }

    @Test
    fun `GET tournaments 에서 status 필터를 지정하면 해당 상태만 반환된다`() {
        val mockMvc = buildMockMvc()
        saveUser(userId, userProfileImage)
        val pendingId = createTournament(mockMvc, "대기중")
        val startedId = createTournament(mockMvc, "진행중")
        addItemsToTournament(mockMvc, startedId, userId, 100L, 200L)
        mockMvc.perform(
            post("/api/v1/tournaments/$startedId/start")
                .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
        )

        mockMvc
            .perform(
                get("/api/v1/tournaments")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                    .param("status", "PENDING"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].tournamentId").value(pendingId))
            .andExpect(jsonPath("$.data[0].participantProfileImages[0]").value(userProfileImage))
    }

    @Test
    fun `GET tournaments 에서 복수 status 필터를 지정하면 해당 상태들만 반환된다`() {
        val mockMvc = buildMockMvc()
        val pendingId = createTournament(mockMvc, "대기중")
        val startedId = createTournament(mockMvc, "진행중")
        addItemsToTournament(mockMvc, startedId, userId, 100L, 200L)
        mockMvc.perform(
            post("/api/v1/tournaments/$startedId/start")
                .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
        )

        mockMvc
            .perform(
                get("/api/v1/tournaments")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                    .param("status", "PENDING", "IN_PROGRESS"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[?(@.tournamentId == $pendingId)]").exists())
            .andExpect(jsonPath("$.data[?(@.tournamentId == $startedId)]").exists())
    }

    @Test
    fun `GET tournaments 에서 토너먼트가 없으면 빈 배열을 반환한다`() {
        val mockMvc = buildMockMvc()

        mockMvc
            .perform(
                get("/api/v1/tournaments")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(0))
    }

    @Test
    fun `GET tournaments 에서 인증 없이 요청하면 401 을 반환한다`() {
        val mockMvc = buildMockMvc()

        mockMvc
            .perform(get("/api/v1/tournaments"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET tournaments-id 는 토너먼트 정보와 히스토리를 반환한다`() {
        val mockMvc = buildMockMvc()
        val (tournamentId, item1Id, item2Id) = startTournamentWith2Items(mockMvc)

        mockMvc.perform(
            post("/api/v1/tournaments/$tournamentId/matches")
                .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"currentRound":2,"firstTournamentItemId":$item1Id,"secondTournamentItemId":$item2Id,"selectedTournamentItemId":$item1Id}""",
                ),
        )

        mockMvc
            .perform(
                get("/api/v1/tournaments/$tournamentId")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.tournamentId").value(tournamentId))
            .andExpect(jsonPath("$.data.startRound").value(2))
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
                    .header(HttpHeaders.AUTHORIZATION, authHeader(otherUserId)),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
    }

    @Test
    fun `GET tournaments-id 에서 존재하지 않는 tournamentId 이면 404 를 반환한다`() {
        val mockMvc = buildMockMvc()

        mockMvc
            .perform(
                get("/api/v1/tournaments/999999")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
    }

    @Test
    fun `DELETE tournaments-id-items-itemId 는 PENDING 토너먼트에서 아이템을 삭제하고 200 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        addItemsToTournament(mockMvc, tournamentId, userId, 100L, 200L)
        val itemId = tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId).first().getId()

        mockMvc
            .perform(
                delete("/api/v1/tournaments/$tournamentId/items/$itemId")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))

        val remaining = tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId)
        assertEquals(1, remaining.size)
        assert(remaining.none { it.getId() == itemId })
    }

    @Test
    fun `DELETE tournaments-id-items-itemId 에서 IN_PROGRESS 토너먼트이면 409 를 반환한다`() {
        val mockMvc = buildMockMvc()
        val (tournamentId, item1Id) = startTournamentWith2Items(mockMvc)

        mockMvc
            .perform(
                delete("/api/v1/tournaments/$tournamentId/items/$item1Id")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.status").value(409))
    }

    @Test
    fun `DELETE tournaments-id-items-itemId 에서 아이템 추가자가 아니고 소유자도 아니면 403 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        addItemsToTournament(mockMvc, tournamentId, userId, 100L, 200L)
        val itemId = tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId).first().getId()

        mockMvc
            .perform(
                delete("/api/v1/tournaments/$tournamentId/items/$itemId")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(otherUserId)),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
    }

    @Test
    fun `DELETE tournaments-id-items-itemId 에서 소유자는 다른 참가자가 추가한 아이템도 삭제할 수 있다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        tournamentUserJpaRepository.save(TournamentUser(tournamentId = tournamentId, userId = otherUserId))
        addItemsToTournament(mockMvc, tournamentId, otherUserId, 300L, 400L)
        val itemId = tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId).first().getId()

        mockMvc
            .perform(
                delete("/api/v1/tournaments/$tournamentId/items/$itemId")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))

        assert(tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId).none { it.getId() == itemId })
    }

    @Test
    fun `DELETE tournaments-id-items-itemId 에서 존재하지 않는 아이템이면 404 를 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)

        mockMvc
            .perform(
                delete("/api/v1/tournaments/$tournamentId/items/999999")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
    }

    @Test
    fun `DELETE tournaments-id-items-itemId 에서 다른 토너먼트 소속 아이템이면 404 를 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId1 = createTournament(mockMvc, "토너먼트1")
        val tournamentId2 = createTournament(mockMvc, "토너먼트2")
        val itemOfTournament2 = tournamentItemJpaRepository.save(
            TournamentItem(tournamentId = tournamentId2, itemId = 999L, userId = userId),
        ).getId()

        mockMvc
            .perform(
                delete("/api/v1/tournaments/$tournamentId1/items/$itemOfTournament2")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
    }

    private fun buildMockMvc(): MockMvc =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    private fun createTournament(
        mockMvc: MockMvc,
        name: String = "테스트 토너먼트",
    ): Long {
        val result =
            mockMvc
                .perform(
                    post("/api/v1/tournaments")
                        .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"$name"}"""),
                ).andReturn()
        return objectMapper.readTree(result.response.contentAsString)["data"]["tournamentId"].asLong()
    }

    private fun saveUser(
        id: UUID,
        profileImage: String,
        nickname: String = "테스트유저",
    ): User = userJpaRepository.save(User(id = id, nickname = nickname, profileImage = profileImage, identityType = IdentityType.MEMBER))


    private fun addItemsToTournament(
        mockMvc: MockMvc,
        tournamentId: Long,
        owner: UUID,
        vararg itemIds: Long,
    ) {
        mockMvc.perform(
            post("/api/v1/tournaments/$tournamentId/items")
                .header(HttpHeaders.AUTHORIZATION, authHeader(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"itemIds":${itemIds.joinToString(",", "[", "]")}}"""),
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
                .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
        )
        val items = tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId)
        return TournamentStart(
            tournamentId = tournamentId,
            item1Id = items[0].getId(),
            item2Id = items[1].getId(),
        )
    }
}
