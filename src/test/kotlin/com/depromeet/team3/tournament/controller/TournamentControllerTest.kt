package com.depromeet.team3.tournament.controller

import com.depromeet.team3.tournament.service.TournamentService
import com.depromeet.team3.tournament.service.dto.AddTournamentItems
import com.depromeet.team3.tournament.service.dto.CreateTournament
import com.depromeet.team3.tournament.service.dto.RecordMatch
import com.depromeet.team3.tournament.service.dto.TournamentHistoryInfo
import com.depromeet.team3.tournament.service.dto.TournamentInfo
import com.depromeet.team3.tournament.service.dto.TournamentItemInfo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class TournamentControllerTest {

    @Mock
    private lateinit var tournamentService: TournamentService

    @InjectMocks
    private lateinit var tournamentController: TournamentController

    private lateinit var mockMvc: MockMvc

    private val userId = UUID.fromString("11111111-2222-3333-4444-555555555555")

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(tournamentController).build()
    }

    @Test
    fun `POST tournaments 는 생성된 tournamentId 를 반환하고 201 을 응답한다`() {
        given(tournamentService.create(userId, CreateTournament("테스트 토너먼트"))).willReturn(1L)

        mockMvc.perform(
            post("/api/v1/tournaments")
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"테스트 토너먼트"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.tournamentId").value(1))
            .andExpect(jsonPath("$.status").value(201))
    }

    @Test
    fun `POST tournaments tournamentId items 는 아이템을 추가하고 200 을 응답한다`() {
        val expectedCommand = AddTournamentItems(tournamentId = 1L, itemIds = listOf(10L, 20L))

        mockMvc.perform(
            post("/api/v1/tournaments/1/items")
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"itemIds":[10,20]}"""),
        )
            .andExpect(status().isOk)

        then(tournamentService).should().addItems(userId, expectedCommand)
    }

    @Test
    fun `POST tournaments tournamentId start 는 토너먼트를 시작하고 200 을 응답한다`() {
        mockMvc.perform(
            post("/api/v1/tournaments/1/start")
                .header("X-User-Id", userId.toString()),
        )
            .andExpect(status().isOk)

        then(tournamentService).should().start(userId, 1L)
    }

    @Test
    fun `POST tournaments tournamentId matches 는 매치를 기록하고 200 을 응답한다`() {
        val expectedRecordMatch = RecordMatch(
            tournamentId = 1L,
            currentRound = 4,
            firstItemId = 10L,
            secondItemId = 20L,
            winnerItemId = 10L,
        )

        mockMvc.perform(
            post("/api/v1/tournaments/1/matches")
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentRound":4,"firstItemId":10,"secondItemId":20,"winnerItemId":10}"""),
        )
            .andExpect(status().isOk)

        then(tournamentService).should().recordMatch(userId, expectedRecordMatch)
    }

    @Test
    fun `GET tournaments id 는 토너먼트 정보를 반환한다`() {
        val tournamentInfo = TournamentInfo(
            tournamentId = 1L,
            items = listOf(
                TournamentItemInfo(tournamentItemId = 1L, itemId = 10L),
                TournamentItemInfo(tournamentItemId = 2L, itemId = 20L),
            ),
            history = listOf(
                TournamentHistoryInfo(
                    currentRound = 2,
                    firstItemId = 1L,
                    secondItemId = 2L,
                    winnerItemId = 1L,
                ),
            ),
        )
        given(tournamentService.getTournamentById(1L, userId)).willReturn(tournamentInfo)

        mockMvc.perform(
            get("/api/v1/tournaments/1")
                .header("X-User-Id", userId.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.tournamentId").value(1))
            .andExpect(jsonPath("$.data.items[0].tournamentItemId").value(1))
            .andExpect(jsonPath("$.data.history[0].currentRound").value(2))
            .andExpect(jsonPath("$.data.history[0].winnerItemId").value(1))
    }
}
