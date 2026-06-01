package com.depromeet.piki.tournament.controller

import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.item.domain.ItemStatus
import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.item.repository.ItemJpaRepository
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.StubImageParsingWorker
import com.depromeet.piki.support.StubItemParsingWorker
import com.depromeet.piki.tournament.domain.TournamentItem
import com.depromeet.piki.tournament.domain.TournamentUser
import com.depromeet.piki.tournament.repository.TournamentItemJpaRepository
import com.depromeet.piki.tournament.repository.TournamentUserJpaRepository
import com.depromeet.piki.user.domain.IdentityType
import com.depromeet.piki.user.domain.User
import com.depromeet.piki.user.repository.UserJpaRepository
import com.depromeet.piki.wishlist.domain.Wish
import com.depromeet.piki.wishlist.repository.WishJpaRepository
import com.depromeet.piki.wishlist.service.WishPersistenceService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Transactional
class TournamentControllerTest : IntegrationTestSupport() {
    @Autowired private lateinit var webApplicationContext: WebApplicationContext

    @Autowired private lateinit var objectMapper: ObjectMapper

    @Autowired private lateinit var tournamentItemJpaRepository: TournamentItemJpaRepository

    @Autowired private lateinit var tournamentUserJpaRepository: TournamentUserJpaRepository

    @Autowired private lateinit var userJpaRepository: UserJpaRepository

    @Autowired private lateinit var itemJpaRepository: ItemJpaRepository

    @Autowired private lateinit var jwtProvider: JwtProvider

    @Autowired private lateinit var wishPersistenceService: WishPersistenceService

    @Autowired private lateinit var wishJpaRepository: WishJpaRepository

    @Autowired private lateinit var stubItemParsingWorker: StubItemParsingWorker

    @Autowired private lateinit var stubImageParsingWorker: StubImageParsingWorker

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
            .andExpect(jsonPath("$.data.tournamentId").isNumber)
    }

    @Test
    fun `POST tournaments-id-items 는 참여자이면 200 과 함께 tournamentItemIds 를 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        val item1Id = saveWishItem()
        val item2Id = saveWishItem()

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/items/wish")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"itemIds":[$item1Id,$item2Id]}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.tournamentItemIds").isArray)
            .andExpect(jsonPath("$.data.tournamentItemIds.length()").value(2))
    }

    @Test
    fun `POST tournaments-id-items 는 아이템 1개도 추가할 수 있다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        val itemId = saveWishItem()

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/items/wish")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"itemIds":[$itemId]}"""),
            ).andExpect(status().isOk)
    }

    @Test
    fun `POST tournaments-id-items 에서 존재하지 않는 아이템 ID 이면 404 를 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        // 위시에는 등록되어 있지만 item 테이블에는 없는 ID — wish 확인 통과 후 item 존재 확인에서 404
        wishJpaRepository.save(Wish(userId = userId, itemId = 999999L))

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/items/wish")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"itemIds":[999999]}"""),
            ).andExpect(status().isNotFound)
    }

    @Test
    fun `POST tournaments-id-items 에서 아직 파싱 중(PROCESSING)인 아이템이면 409 를 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        val processingItemId = itemJpaRepository.save(Item(status = ItemStatus.PROCESSING)).getId()
        // 위시에도 등록 — wish 확인 통과 후 READY 상태 확인에서 409
        wishJpaRepository.save(Wish(userId = userId, itemId = processingItemId))

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/items/wish")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"itemIds":[$processingItemId]}"""),
            ).andExpect(status().isConflict)
    }

    @Test
    fun `POST tournaments-id-items 에서 빈 itemIds 는 400 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/items/wish")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"itemIds":[]}"""),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `POST tournaments-id-items 에서 토너먼트 참여자가 아니면 403 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/items/wish")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(otherUserId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"itemIds":[100,200]}"""),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `POST tournaments-id-start 는 아이템이 있는 PENDING 토너먼트를 시작하고 가격 오름차순 정렬된 아이템 목록을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        // 비싼 아이템을 먼저 추가 — DB 조회 순서(id ASC)와 가격 순서가 달라야 정렬이 실제로 검증된다
        val item2Id = wishPersistenceService.persist(userId, Item(name = "아디다스 울트라부스트", currentPrice = 189_000, currency = "KRW")).item.getId()
        val item1Id = wishPersistenceService.persist(userId, Item(name = "나이키 에어맥스", currentPrice = 129_000, currency = "KRW")).item.getId()
        addItemsToTournament(mockMvc, tournamentId, userId, item2Id, item1Id)

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/start")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items").isArray)
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].tournamentItemId").isNumber)
            .andExpect(jsonPath("$.data.items[0].name").value("나이키 에어맥스"))
            .andExpect(jsonPath("$.data.items[0].price").value(129_000))
            .andExpect(jsonPath("$.data.items[0].currency").value("KRW"))
            .andExpect(jsonPath("$.data.items[1].tournamentItemId").isNumber)
            .andExpect(jsonPath("$.data.items[1].name").value("아디다스 울트라부스트"))
            .andExpect(jsonPath("$.data.items[1].price").value(189_000))
            .andExpect(jsonPath("$.data.items[1].currency").value("KRW"))
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
    }

    @Test
    fun `POST tournaments-id-matches 에서 이미 탈락한 아이템으로 매치를 시도하면 409 를 반환한다`() {
        val mockMvc = buildMockMvc()
        val item1Id = saveWishItem(name = "아이템1", price = 10_000)
        val item2Id = saveWishItem(name = "아이템2", price = 20_000)
        val item3Id = saveWishItem(name = "아이템3", price = 30_000)
        val item4Id = saveWishItem(name = "아이템4", price = 40_000)
        val tournamentId = createTournament(mockMvc)
        addItemsToTournament(mockMvc, tournamentId, userId, item1Id, item2Id, item3Id, item4Id)
        mockMvc.perform(
            post("/api/v1/tournaments/$tournamentId/start")
                .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
        )
        val items = tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId)
        val ti1 = items[0].getId()
        val ti2 = items[1].getId()
        val ti3 = items[2].getId()
        val ti4 = items[3].getId()

        // round-4 첫 번째 매치: ti1 승, ti2 탈락
        mockMvc.perform(
            post("/api/v1/tournaments/$tournamentId/matches")
                .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentRound":4,"firstTournamentItemId":$ti1,"secondTournamentItemId":$ti2,"selectedTournamentItemId":$ti1}"""),
        ).andExpect(status().isOk)

        // 탈락한 ti2 로 다시 매치 시도 → 409
        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/matches")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"currentRound":4,"firstTournamentItemId":$ti2,"secondTournamentItemId":$ti3,"selectedTournamentItemId":$ti3}"""),
            ).andExpect(status().isConflict)
    }

    @Test
    fun `POST tournaments-id-matches 에서 currentRound 가 예상 라운드와 다르면 400 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val (tournamentId, item1Id, item2Id) = startTournamentWith2Items(mockMvc)

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/matches")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"currentRound":4,"firstTournamentItemId":$item1Id,"secondTournamentItemId":$item2Id,"selectedTournamentItemId":$item1Id}""",
                    ),
            ).andExpect(status().isBadRequest)
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
        addItemsToTournament(mockMvc, startedId, userId, saveWishItem(), saveWishItem())
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
        addItemsToTournament(mockMvc, startedId, userId, saveWishItem(), saveWishItem())
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
    fun `GET tournaments-id 는 COMPLETED 토너먼트에서 1위부터 4위까지 순위 결과를 반환한다`() {
        val mockMvc = buildMockMvc()
        val item1Id = saveWishItem(name = "1위아이템", price = 10_000)
        val item2Id = saveWishItem(name = "2위아이템", price = 20_000)
        val item3Id = saveWishItem(name = "3위아이템", price = 30_000)
        val item4Id = saveWishItem(name = "4위아이템", price = 40_000)
        val tournamentId = createTournament(mockMvc)
        addItemsToTournament(mockMvc, tournamentId, userId, item1Id, item2Id, item3Id, item4Id)
        mockMvc.perform(
            post("/api/v1/tournaments/$tournamentId/start")
                .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
        )
        // start 는 가격 오름차순 반환 → ti1~ti4 순 — 클라이언트 페어링: [0]vs[1], [2]vs[3]
        val items = tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId)
        val ti1 = items[0].getId()
        val ti2 = items[1].getId()
        val ti3 = items[2].getId()
        val ti4 = items[3].getId()

        // round-4: ti1 vs ti2 → ti1 승, ti3 vs ti4 → ti3 승
        mockMvc.perform(
            post("/api/v1/tournaments/$tournamentId/matches")
                .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentRound":4,"firstTournamentItemId":$ti1,"secondTournamentItemId":$ti2,"selectedTournamentItemId":$ti1}"""),
        ).andExpect(status().isOk)
        mockMvc.perform(
            post("/api/v1/tournaments/$tournamentId/matches")
                .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentRound":4,"firstTournamentItemId":$ti3,"secondTournamentItemId":$ti4,"selectedTournamentItemId":$ti3}"""),
        ).andExpect(status().isOk)
        // round-2 결승: ti1 vs ti3 → ti1 승
        mockMvc.perform(
            post("/api/v1/tournaments/$tournamentId/matches")
                .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentRound":2,"firstTournamentItemId":$ti1,"secondTournamentItemId":$ti3,"selectedTournamentItemId":$ti1}"""),
        ).andExpect(status().isOk)

        mockMvc
            .perform(
                get("/api/v1/tournaments/$tournamentId")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.completed.result.length()").value(4))
            // 1위: ti1, 2위: ti3(결승 패배), 3위: ti2(준결승 패배, tiId 낮음), 4위: ti4
            .andExpect(jsonPath("$.data.completed.result[0].rank").value(1))
            .andExpect(jsonPath("$.data.completed.result[0].tournamentItemId").value(ti1))
            .andExpect(jsonPath("$.data.completed.result[0].name").value("1위아이템"))
            .andExpect(jsonPath("$.data.completed.result[1].rank").value(2))
            .andExpect(jsonPath("$.data.completed.result[1].tournamentItemId").value(ti3))
            .andExpect(jsonPath("$.data.completed.result[2].rank").value(3))
            .andExpect(jsonPath("$.data.completed.result[2].tournamentItemId").value(ti2))
            .andExpect(jsonPath("$.data.completed.result[3].rank").value(4))
            .andExpect(jsonPath("$.data.completed.result[3].tournamentItemId").value(ti4))
            .andExpect(jsonPath("$.data.pending").doesNotExist())
            .andExpect(jsonPath("$.data.inProgress").doesNotExist())
    }

    @Test
    fun `GET tournaments-id 는 2개 아이템 COMPLETED 토너먼트에서 준결승 없이 1위와 2위만 반환한다`() {
        val mockMvc = buildMockMvc()
        val (tournamentId, ti1, ti2) = startTournamentWith2Items(mockMvc)
        // 결승만 치름 — 준결승 히스토리 없음
        mockMvc.perform(
            post("/api/v1/tournaments/$tournamentId/matches")
                .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentRound":2,"firstTournamentItemId":$ti1,"secondTournamentItemId":$ti2,"selectedTournamentItemId":$ti1}"""),
        ).andExpect(status().isOk)

        mockMvc
            .perform(
                get("/api/v1/tournaments/$tournamentId")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.completed.result.length()").value(2))
            .andExpect(jsonPath("$.data.completed.result[0].rank").value(1))
            .andExpect(jsonPath("$.data.completed.result[0].tournamentItemId").value(ti1))
            .andExpect(jsonPath("$.data.completed.result[1].rank").value(2))
            .andExpect(jsonPath("$.data.completed.result[1].tournamentItemId").value(ti2))
            .andExpect(jsonPath("$.data.pending").doesNotExist())
            .andExpect(jsonPath("$.data.inProgress").doesNotExist())
    }

    @Test
    fun `GET tournaments-id 는 32개 아이템 토너먼트에서 6번 선택 후 현재 라운드 미대결 생존 아이템 20개를 가격 오름차순으로 반환한다`() {
        val mockMvc = buildMockMvc()
        // 가격 1_000 ~ 32_000 순으로 32개 아이템 생성 (삽입 순서 = 가격 오름차순)
        val itemIds = (1..32).map { i -> saveWishItem(name = "아이템$i", price = i * 1_000) }.toLongArray()
        val tournamentId = createTournament(mockMvc)
        addItemsToTournament(mockMvc, tournamentId, userId, *itemIds)
        mockMvc.perform(
            post("/api/v1/tournaments/$tournamentId/start")
                .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
        )

        val allItems = tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId)
        // 6번의 매치 기록 — 1라운드(currentRound=32), 각 2개씩 소진 → ti[0]~ti[11] 등장
        repeat(6) { i ->
            val first = allItems[i * 2].getId()
            val second = allItems[i * 2 + 1].getId()
            mockMvc.perform(
                post("/api/v1/tournaments/$tournamentId/matches")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"currentRound":32,"firstTournamentItemId":$first,"secondTournamentItemId":$second,"selectedTournamentItemId":$first}"""),
            ).andExpect(status().isOk)
        }

        val lastFirst = allItems[10].getId()
        val lastSecond = allItems[11].getId()

        mockMvc
            .perform(
                get("/api/v1/tournaments/$tournamentId")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.inProgress.currentRound").value(32))
            // 마지막 히스토리 = 6번째 매치
            .andExpect(jsonPath("$.data.inProgress.lastHistory.currentRound").value(32))
            .andExpect(jsonPath("$.data.inProgress.lastHistory.firstTournamentItemId").value(lastFirst))
            .andExpect(jsonPath("$.data.inProgress.lastHistory.secondTournamentItemId").value(lastSecond))
            .andExpect(jsonPath("$.data.inProgress.lastHistory.selectedTournamentItemId").value(lastFirst))
            // round-32 미대결 생존 아이템 20개(ti[12]~ti[31]) 가격 오름차순
            .andExpect(jsonPath("$.data.inProgress.remainingItems.length()").value(20))
            .andExpect(jsonPath("$.data.inProgress.remainingItems[0].price").value(13_000))
            .andExpect(jsonPath("$.data.inProgress.remainingItems[19].price").value(32_000))
    }

    @Test
    fun `GET tournaments-id 는 IN_PROGRESS 토너먼트에서 마지막 히스토리와 현재 라운드 미대결 생존 아이템을 가격 오름차순으로 반환한다`() {
        val mockMvc = buildMockMvc()
        val item1Id = saveWishItem(name = "아이템1", price = 10_000)
        val item2Id = saveWishItem(name = "아이템2", price = 40_000)
        val item3Id = saveWishItem(name = "아이템3", price = 20_000)
        val item4Id = saveWishItem(name = "아이템4", price = 30_000)
        val tournamentId = createTournament(mockMvc)
        addItemsToTournament(mockMvc, tournamentId, userId, item1Id, item2Id, item3Id, item4Id)
        mockMvc.perform(
            post("/api/v1/tournaments/$tournamentId/start")
                .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
        )
        val items = tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId)
        val ti1 = items[0].getId()
        val ti2 = items[1].getId()

        mockMvc.perform(
            post("/api/v1/tournaments/$tournamentId/matches")
                .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentRound":4,"firstTournamentItemId":$ti1,"secondTournamentItemId":$ti2,"selectedTournamentItemId":$ti1}"""),
        )

        mockMvc
            .perform(
                get("/api/v1/tournaments/$tournamentId")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.pending").doesNotExist())
            .andExpect(jsonPath("$.data.inProgress.currentRound").value(4))
            .andExpect(jsonPath("$.data.inProgress.lastHistory.currentRound").value(4))
            .andExpect(jsonPath("$.data.inProgress.lastHistory.firstTournamentItemId").value(ti1))
            .andExpect(jsonPath("$.data.inProgress.lastHistory.secondTournamentItemId").value(ti2))
            .andExpect(jsonPath("$.data.inProgress.lastHistory.selectedTournamentItemId").value(ti1))
            // round-4 미대결 생존 아이템 2개(item3, item4) 가격 오름차순
            .andExpect(jsonPath("$.data.inProgress.remainingItems.length()").value(2))
            .andExpect(jsonPath("$.data.inProgress.remainingItems[0].price").value(20_000))
            .andExpect(jsonPath("$.data.inProgress.remainingItems[1].price").value(30_000))
    }

    @Test
    fun `GET tournaments-id 는 IN_PROGRESS 토너먼트에서 매치 기록이 없으면 lastHistory 가 없고 전체 아이템을 가격 오름차순으로 반환한다`() {
        val mockMvc = buildMockMvc()
        val item1Id = saveWishItem(name = "아이템1", price = 30_000)
        val item2Id = saveWishItem(name = "아이템2", price = 10_000)
        val tournamentId = createTournament(mockMvc)
        addItemsToTournament(mockMvc, tournamentId, userId, item1Id, item2Id)
        mockMvc.perform(
            post("/api/v1/tournaments/$tournamentId/start")
                .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
        )

        mockMvc
            .perform(
                get("/api/v1/tournaments/$tournamentId")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.inProgress.currentRound").value(2))
            .andExpect(jsonPath("$.data.inProgress.lastHistory").doesNotExist())
            .andExpect(jsonPath("$.data.inProgress.remainingItems.length()").value(2))
            .andExpect(jsonPath("$.data.inProgress.remainingItems[0].price").value(10_000))
            .andExpect(jsonPath("$.data.inProgress.remainingItems[1].price").value(30_000))
    }

    @Test
    fun `GET tournaments-id 는 PENDING 토너먼트의 아이템 목록을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        addItemsToTournament(mockMvc, tournamentId, userId, saveWishItem(), saveWishItem())

        mockMvc
            .perform(
                get("/api/v1/tournaments/$tournamentId")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.pending.items.length()").value(2))
            .andExpect(jsonPath("$.data.inProgress").doesNotExist())
            .andExpect(jsonPath("$.data.completed").doesNotExist())
    }

    @Test
    fun `GET tournaments-id 는 PENDING 상태에서 READY 아이템의 status 가 READY 로 내려온다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        val itemId = saveWishItem(name = "나이키 에어맥스", price = 99_000)
        addItemsToTournament(mockMvc, tournamentId, userId, itemId)

        mockMvc
            .perform(
                get("/api/v1/tournaments/$tournamentId")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.pending.items[0].status").value("READY"))
            .andExpect(jsonPath("$.data.pending.items[0].name").value("나이키 에어맥스"))
            .andExpect(jsonPath("$.data.pending.items[0].price").value(99_000))
            .andExpect(jsonPath("$.data.pending.items[0].tournamentItemId").isNumber)
            .andExpect(jsonPath("$.data.pending.items[0].itemId").value(itemId))
    }

    @Test
    fun `GET tournaments-id 는 PENDING 상태에서 PROCESSING 아이템이 status=PROCESSING 으로 목록에 포함되고 name·price·imageUrl 은 없다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        val processingItemId = itemJpaRepository.save(Item(status = ItemStatus.PROCESSING)).getId()
        tournamentItemJpaRepository.save(TournamentItem(tournamentId = tournamentId, itemId = processingItemId, userId = userId))

        mockMvc
            .perform(
                get("/api/v1/tournaments/$tournamentId")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.pending.items.length()").value(1))
            .andExpect(jsonPath("$.data.pending.items[0].status").value("PROCESSING"))
            .andExpect(jsonPath("$.data.pending.items[0].itemId").value(processingItemId))
            .andExpect(jsonPath("$.data.pending.items[0].name").doesNotExist())
            .andExpect(jsonPath("$.data.pending.items[0].price").doesNotExist())
            .andExpect(jsonPath("$.data.pending.items[0].imageUrl").doesNotExist())
    }

    @Test
    fun `GET tournaments-id 는 IN_PROGRESS 상태에서 remainingItems 의 각 아이템에 status 가 포함된다`() {
        val mockMvc = buildMockMvc()
        val item1Id = saveWishItem(name = "아이템1", price = 10_000)
        val item2Id = saveWishItem(name = "아이템2", price = 20_000)
        val tournamentId = createTournament(mockMvc)
        addItemsToTournament(mockMvc, tournamentId, userId, item1Id, item2Id)
        mockMvc.perform(
            post("/api/v1/tournaments/$tournamentId/start")
                .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
        )

        mockMvc
            .perform(
                get("/api/v1/tournaments/$tournamentId")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.inProgress.remainingItems.length()").value(2))
            .andExpect(jsonPath("$.data.inProgress.remainingItems[0].status").value("READY"))
            .andExpect(jsonPath("$.data.inProgress.remainingItems[1].status").value("READY"))
    }

    @Test
    fun `GET tournaments-id 는 PENDING 토너먼트 응답에 참여자 정보를 포함한다`() {
        val mockMvc = buildMockMvc()
        saveUser(userId, userProfileImage)
        val tournamentId = createTournament(mockMvc)

        mockMvc
            .perform(
                get("/api/v1/tournaments/$tournamentId")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.pending.participants").isArray)
            .andExpect(jsonPath("$.data.pending.participants.length()").value(1))
            .andExpect(jsonPath("$.data.pending.participants[0].userId").isString)
            .andExpect(jsonPath("$.data.pending.participants[0].nickname").isString)
            .andExpect(jsonPath("$.data.pending.participants[0].profileImage").value(userProfileImage))
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
    }

    @Test
    fun `GET tournaments-id 에서 존재하지 않는 tournamentId 이면 404 를 반환한다`() {
        val mockMvc = buildMockMvc()

        mockMvc
            .perform(
                get("/api/v1/tournaments/999999")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE tournaments-id-items-itemId 는 PENDING 토너먼트에서 아이템을 삭제하고 200 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        addItemsToTournament(mockMvc, tournamentId, userId, saveWishItem(), saveWishItem())
        val itemId = tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId).first().getId()

        mockMvc
            .perform(
                delete("/api/v1/tournaments/$tournamentId/items/$itemId")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)

        val remaining = tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId)
        assertEquals(1, remaining.size)
        assertTrue(remaining.none { it.getId() == itemId })
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
    }

    @Test
    fun `DELETE tournaments-id-items-itemId 에서 아이템 추가자가 아니고 소유자도 아니면 403 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        addItemsToTournament(mockMvc, tournamentId, userId, saveWishItem(), saveWishItem())
        val itemId = tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId).first().getId()

        mockMvc
            .perform(
                delete("/api/v1/tournaments/$tournamentId/items/$itemId")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(otherUserId)),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `DELETE tournaments-id-items-itemId 에서 소유자는 다른 참가자가 추가한 아이템도 삭제할 수 있다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        tournamentUserJpaRepository.save(TournamentUser(tournamentId = tournamentId, userId = otherUserId))
        addItemsToTournament(mockMvc, tournamentId, otherUserId, saveWishItem(otherUserId), saveWishItem(otherUserId))
        val itemId = tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId).first().getId()

        mockMvc
            .perform(
                delete("/api/v1/tournaments/$tournamentId/items/$itemId")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)

        assertTrue(
            tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId).none { it.getId() == itemId },
        )
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
    }

    @Test
    fun `DELETE tournaments-id-items-itemId 에서 다른 토너먼트 소속 아이템이면 404 를 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId1 = createTournament(mockMvc, "토너먼트1")
        val tournamentId2 = createTournament(mockMvc, "토너먼트2")
        val itemOfTournament2 =
            tournamentItemJpaRepository
                .save(
                    TournamentItem(tournamentId = tournamentId2, itemId = 999L, userId = userId),
                ).getId()

        mockMvc
            .perform(
                delete("/api/v1/tournaments/$tournamentId1/items/$itemOfTournament2")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isNotFound)
    }

    @Test
    fun `POST tournaments-id-items-link 는 참여자이면 PROCESSING 아이템을 생성하고 tournamentItemId 를 반환한다`() {
        stubItemParsingWorker.enabled = false
        try {
            val mockMvc = buildMockMvc()
            val tournamentId = createTournament(mockMvc)

            val result =
                mockMvc
                    .perform(
                        post("/api/v1/tournaments/$tournamentId/items/link")
                            .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"url":"https://example.com/product"}"""),
                    ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.data.tournamentItemId").isNumber)
                    .andReturn()

            val tournamentItemId = objectMapper.readTree(result.response.contentAsString)["data"]["tournamentItemId"].asLong()
            val tournamentItem = tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId).also {
                assertEquals(1, it.size)
            }.first()
            assertEquals(tournamentItemId, tournamentItem.getId())
            assertEquals(ItemStatus.PROCESSING, itemJpaRepository.findById(tournamentItem.itemId).orElseThrow().status)
        } finally {
            stubItemParsingWorker.enabled = true
        }
    }

    @Test
    fun `POST tournaments-id-items-link 에서 url 이 빈 값이면 400 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/items/link")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"url":""}"""),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `POST tournaments-id-items-link 에서 토너먼트 참여자가 아니면 403 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/items/link")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(otherUserId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"url":"https://example.com/product"}"""),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `POST tournaments-id-items-link 에서 토너먼트가 없으면 404 를 반환한다`() {
        val mockMvc = buildMockMvc()

        mockMvc
            .perform(
                post("/api/v1/tournaments/999999/items/link")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"url":"https://example.com/product"}"""),
            ).andExpect(status().isNotFound)
    }

    @Test
    fun `POST tournaments-id-items-images 는 참여자이면 PROCESSING 아이템을 생성하고 tournamentItemIds 를 반환한다`() {
        stubImageParsingWorker.enabled = false
        try {
            val mockMvc = buildMockMvc()
            val tournamentId = createTournament(mockMvc)
            val image1 = MockMultipartFile("images", "img1.jpg", "image/jpeg", ByteArray(100) { 1 })
            val image2 = MockMultipartFile("images", "img2.jpg", "image/jpeg", ByteArray(100) { 2 })

            mockMvc
                .perform(
                    multipart("/api/v1/tournaments/$tournamentId/items/images")
                        .file(image1)
                        .file(image2)
                        .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.tournamentItemIds").isArray)
                .andExpect(jsonPath("$.data.tournamentItemIds.length()").value(2))

            assertEquals(2, tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId).size)
        } finally {
            stubImageParsingWorker.enabled = true
        }
    }

    @Test
    fun `POST tournaments-id-items-images 에서 토너먼트 참여자가 아니면 403 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        val image = MockMultipartFile("images", "test.jpg", "image/jpeg", ByteArray(100) { 1 })

        mockMvc
            .perform(
                multipart("/api/v1/tournaments/$tournamentId/items/images")
                    .file(image)
                    .header(HttpHeaders.AUTHORIZATION, authHeader(otherUserId)),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `POST tournaments-id-items-images 에서 이미지 6개 이상이면 400 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        val images = (1..6).map { i -> MockMultipartFile("images", "img$i.jpg", "image/jpeg", ByteArray(100) { 1 }) }

        val request = images.fold(multipart("/api/v1/tournaments/$tournamentId/items/images")) { req, file ->
            req.file(file)
        }.header(HttpHeaders.AUTHORIZATION, authHeader(userId))

        mockMvc
            .perform(request)
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST tournaments-id-items-images 에서 이미지 파트를 보내지 않으면 400 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)

        // .file(...) 없이 images 파트를 아예 생략 — required=false + orEmpty 로 서비스 검증(개수 0)에 닿아 400.
        mockMvc
            .perform(
                multipart("/api/v1/tournaments/$tournamentId/items/images")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `POST tournaments-id-items 에서 위시리스트에 없는 아이템이면 403 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        // saveItem 은 위시 없이 아이템만 저장 — wish 소유 확인에서 실패해야 한다
        val itemId = saveItem()

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/items/wish")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"itemIds":[$itemId]}"""),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `POST tournaments-id-items-link 에서 32개 초과 시 400 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        val full32 = (1..32).map { saveWishItem() }.toLongArray()
        addItemsToTournament(mockMvc, tournamentId, userId, *full32)

        mockMvc
            .perform(
                post("/api/v1/tournaments/$tournamentId/items/link")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"url":"https://example.com/product"}"""),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `POST tournaments-id-items-images 에서 32개 초과 시 400 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        val full32 = (1..32).map { saveWishItem() }.toLongArray()
        addItemsToTournament(mockMvc, tournamentId, userId, *full32)
        val image = MockMultipartFile("images", "img.jpg", "image/jpeg", ByteArray(100) { 1 })

        mockMvc
            .perform(
                multipart("/api/v1/tournaments/$tournamentId/items/images")
                    .file(image)
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isBadRequest)
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
    ): User =
        userJpaRepository.save(
            User(id = id, nickname = nickname, profileImage = profileImage, identityType = IdentityType.MEMBER),
        )

    private fun addItemsToTournament(
        mockMvc: MockMvc,
        tournamentId: Long,
        owner: UUID,
        vararg itemIds: Long,
    ) {
        mockMvc.perform(
            post("/api/v1/tournaments/$tournamentId/items/wish")
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

    private fun saveItem(name: String = "테스트 아이템"): Long =
        itemJpaRepository.save(Item(name = name)).getId()

    // 위시리스트에도 등록된 READY 아이템 생성 — /items/wish 엔드포인트용
    private fun saveWishItem(owner: UUID = userId, name: String = "테스트 아이템", price: Int = 10_000): Long =
        wishPersistenceService.persist(owner, Item(name = name, currentPrice = price, currency = "KRW")).item.getId()

    @Test
    fun `GET tournaments-id-items-tournamentItemId 는 READY 아이템의 이름·가격·이미지·status 를 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        val itemId = saveWishItem(name = "나이키 에어맥스", price = 129_000)
        addItemsToTournament(mockMvc, tournamentId, userId, itemId)
        val tournamentItemId = tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId).first().getId()

        mockMvc
            .perform(
                get("/api/v1/tournaments/$tournamentId/items/$tournamentItemId")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.tournamentItemId").value(tournamentItemId))
            .andExpect(jsonPath("$.data.itemId").value(itemId))
            .andExpect(jsonPath("$.data.name").value("나이키 에어맥스"))
            .andExpect(jsonPath("$.data.price").value(129_000))
            .andExpect(jsonPath("$.data.currency").value("KRW"))
            .andExpect(jsonPath("$.data.status").value("READY"))
            // 이미지·위시 등록 아이템은 sourceUrl 이 없어 응답에 포함되지 않는다
            .andExpect(jsonPath("$.data.sourceUrl").doesNotExist())
    }

    @Test
    fun `GET tournaments-id-items-tournamentItemId 는 링크 등록 아이템의 sourceUrl 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        val sourceUrl = "https://www.nike.com/kr/t/air-max/example"
        val linkItem = itemJpaRepository.save(
            Item(
                link = ProductLink.parse(sourceUrl),
                name = "나이키",
                currentPrice = 100_000,
                currency = "KRW",
            ),
        )
        tournamentItemJpaRepository.save(TournamentItem(tournamentId = tournamentId, itemId = linkItem.getId(), userId = userId))
        val tournamentItemId = tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId).first().getId()

        mockMvc
            .perform(
                get("/api/v1/tournaments/$tournamentId/items/$tournamentItemId")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.sourceUrl").value(sourceUrl))
            .andExpect(jsonPath("$.data.status").value("READY"))
    }

    @Test
    fun `GET tournaments-id-items-tournamentItemId 는 PROCESSING 아이템이면 name·price·imageUrl 이 응답에 없고 status 가 PROCESSING 이다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        val processingItemId = itemJpaRepository.save(Item(status = ItemStatus.PROCESSING)).getId()
        tournamentItemJpaRepository.save(TournamentItem(tournamentId = tournamentId, itemId = processingItemId, userId = userId))
        val tournamentItemId = tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId).first().getId()

        mockMvc
            .perform(
                get("/api/v1/tournaments/$tournamentId/items/$tournamentItemId")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("PROCESSING"))
            .andExpect(jsonPath("$.data.name").doesNotExist())
            .andExpect(jsonPath("$.data.price").doesNotExist())
            .andExpect(jsonPath("$.data.imageUrl").doesNotExist())
    }

    @Test
    fun `GET tournaments-id-items-tournamentItemId 에서 토너먼트 참여자가 아니면 403 을 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)
        addItemsToTournament(mockMvc, tournamentId, userId, saveWishItem())
        val tournamentItemId = tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId).first().getId()

        mockMvc
            .perform(
                get("/api/v1/tournaments/$tournamentId/items/$tournamentItemId")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(otherUserId)),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `GET tournaments-id-items-tournamentItemId 에서 존재하지 않는 tournamentId 이면 404 를 반환한다`() {
        val mockMvc = buildMockMvc()

        mockMvc
            .perform(
                get("/api/v1/tournaments/999999/items/1")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isNotFound)
    }

    @Test
    fun `GET tournaments-id-items-tournamentItemId 에서 존재하지 않는 tournamentItemId 이면 404 를 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId = createTournament(mockMvc)

        mockMvc
            .perform(
                get("/api/v1/tournaments/$tournamentId/items/999999")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isNotFound)
    }

    @Test
    fun `GET tournaments-id-items-tournamentItemId 에서 다른 토너먼트 소속 아이템이면 404 를 반환한다`() {
        val mockMvc = buildMockMvc()
        val tournamentId1 = createTournament(mockMvc, "토너먼트1")
        val tournamentId2 = createTournament(mockMvc, "토너먼트2")
        addItemsToTournament(mockMvc, tournamentId2, userId, saveWishItem())
        val itemOfTournament2 = tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId2).first().getId()

        mockMvc
            .perform(
                get("/api/v1/tournaments/$tournamentId1/items/$itemOfTournament2")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isNotFound)
    }

    private fun startTournamentWith2Items(mockMvc: MockMvc): TournamentStart {
        val tournamentId = createTournament(mockMvc)
        addItemsToTournament(mockMvc, tournamentId, userId, saveWishItem(name = "아이템1"), saveWishItem(name = "아이템2"))
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
