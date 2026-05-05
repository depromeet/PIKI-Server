package com.depromeet.team3.tournament.controller

import com.depromeet.team3.tournament.service.TournamentService
import com.depromeet.team3.tournament.service.dto.TournamentHistoryInfo
import com.depromeet.team3.tournament.service.dto.TournamentInfo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import com.depromeet.team3.tournament.service.dto.RecordMatch
import com.depromeet.team3.tournament.service.dto.StartTournament
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
        given(tournamentService.start(userId, StartTournament("테스트 토너먼트", 8, (1L..8L).toList()))).willReturn(1L)

        mockMvc.perform(
            post("/api/v1/tournaments")
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"name":"테스트 토너먼트","round":8,"wishItemIds":[1,2,3,4,5,6,7,8]}""",
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.tournamentId").value(1))
            .andExpect(jsonPath("$.status").value(201))
    }

    @Test
    fun `POST tournaments tournamentId matches 는 매치를 기록하고 200 을 응답한다`() {
        val expectedRecordMatch = RecordMatch(
            tournamentId = 1L,
            currentRound = 4,
            firstWishItemId = 10L,
            secondWishItemId = 20L,
            winnerWishItemId = 10L,
        )

        mockMvc.perform(
            post("/api/v1/tournaments/1/matches")
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"currentRound":4,"firstWishItemId":10,"secondWishItemId":20,"winnerWishItemId":10}""",
                ),
        )
            .andExpect(status().isOk)

        then(tournamentService).should().recordMatch(userId, expectedRecordMatch)
    }

    @Test
    fun `GET tournaments id 는 토너먼트 정보를 반환한다`() {
        val tournamentInfo = TournamentInfo(
            tournamentId = 1L,
            finalWinnerWishItemId = 10L,
            history = listOf(
                TournamentHistoryInfo(
                    currentRound = 2,
                    firstWishItemId = 10L,
                    secondWishItemId = 20L,
                    winnerWishItemId = 10L,
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
            .andExpect(jsonPath("$.data.finalWinnerWishItemId").value(10))
            .andExpect(jsonPath("$.data.history[0].currentRound").value(2))
            .andExpect(jsonPath("$.data.history[0].winnerWishItemId").value(10))
    }
}
