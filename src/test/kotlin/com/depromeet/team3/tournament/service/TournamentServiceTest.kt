package com.depromeet.team3.tournament.service

import com.depromeet.team3.common.domain.LongBaseEntity
import com.depromeet.team3.product.domain.ProductLink
import com.depromeet.team3.tournament.domain.Tournament
import com.depromeet.team3.tournament.domain.TournamentHistory
import com.depromeet.team3.tournament.domain.TournamentStatus
import com.depromeet.team3.tournament.repository.TournamentRepository
import com.depromeet.team3.tournament.service.dto.RecordMatch
import com.depromeet.team3.tournament.service.dto.StartTournament
import com.depromeet.team3.wishlist.domain.Wish
import com.depromeet.team3.wishlist.repository.WishRepository
import com.depromeet.team3.wishlist.service.WishException
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TournamentServiceTest {
    private class TestWishRepository(
        private val allOwned: Boolean = true,
    ) : WishRepository {
        override fun save(wish: Wish): Wish = wish

        override fun existsByUserIdAndProductLink(
            userId: UUID,
            link: ProductLink,
        ): Boolean = false

        override fun countByIdsAndUserId(
            ids: List<Long>,
            userId: UUID,
        ): Long = if (allOwned) ids.size.toLong() else 0L
    }

    private class TestTournamentRepository : TournamentRepository {
        private var idSeq = 1L
        val tournaments = mutableMapOf<Long, Tournament>()
        val histories = mutableListOf<TournamentHistory>()

        override fun saveTournament(tournament: Tournament): Long {
            val id = idSeq++
            tournaments[id] = tournament
            setId(tournament, id)
            return id
        }

        override fun saveHistory(history: TournamentHistory) {
            histories.add(history)
        }

        override fun findTournamentById(tournamentId: Long): Tournament? = tournaments[tournamentId]

        override fun findTournamentHistoriesByTournamentId(tournamentId: Long): List<TournamentHistory> =
            histories.filter { it.tournamentId == tournamentId }

        private fun setId(
            tournament: Tournament,
            id: Long,
        ) {
            val field = LongBaseEntity::class.java.getDeclaredField("id")
            field.isAccessible = true
            field.set(tournament, id)
        }
    }

    private val repository = TestTournamentRepository()
    private val wishRepository = TestWishRepository()
    private val service = TournamentService(repository, wishRepository)
    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()

    @Test
    fun `start 는 저장된 토너먼트 ID 를 반환한다`() {
        val id = service.start(userId, StartTournament("내 토너먼트", round = 16, wishItemIds = (1L..16L).toList()))

        assertEquals(1L, id)
        assertEquals(1, repository.tournaments.size)
    }

    @Test
    fun `recordMatch 는 히스토리를 저장한다`() {
        val tournamentId = service.start(userId, StartTournament("토너먼트", round = 8, wishItemIds = (1L..8L).toList()))
        val match =
            RecordMatch(
                tournamentId = tournamentId,
                currentRound = 4,
                firstWishItemId = 1L,
                secondWishItemId = 2L,
                winnerWishItemId = 1L,
            )

        service.recordMatch(userId, match)

        assertEquals(1, repository.histories.size)
        assertEquals(1L, repository.histories[0].winnerWishItemId)
    }

    @Test
    fun `recordMatch 에서 currentRound 가 2 면 토너먼트가 COMPLETED 로 완료된다`() {
        val tournamentId = service.start(userId, StartTournament("결승 토너먼트", round = 2, wishItemIds = listOf(10L, 20L)))
        val finalMatch =
            RecordMatch(
                tournamentId = tournamentId,
                currentRound = 2,
                firstWishItemId = 10L,
                secondWishItemId = 20L,
                winnerWishItemId = 10L,
            )

        service.recordMatch(userId, finalMatch)

        val tournament = repository.tournaments[tournamentId]!!
        assertEquals(TournamentStatus.COMPLETED, tournament.status)
        assertEquals(10L, tournament.finalWinnerWishItemId)
    }

    @Test
    fun `recordMatch 에서 존재하지 않는 tournamentId 면 예외가 발생한다`() {
        val match =
            RecordMatch(
                tournamentId = 999L,
                currentRound = 2,
                firstWishItemId = 1L,
                secondWishItemId = 2L,
                winnerWishItemId = 1L,
            )

        assertFailsWith<TournamentException> {
            service.recordMatch(userId, match)
        }
    }

    @Test
    fun `recordMatch 에서 다른 사용자의 토너먼트면 예외가 발생한다`() {
        val tournamentId = service.start(userId, StartTournament("내 토너먼트", round = 4, wishItemIds = (1L..4L).toList()))
        val match =
            RecordMatch(
                tournamentId = tournamentId,
                currentRound = 4,
                firstWishItemId = 1L,
                secondWishItemId = 2L,
                winnerWishItemId = 1L,
            )

        assertFailsWith<TournamentException> {
            service.recordMatch(otherUserId, match)
        }
    }

    @Test
    fun `recordMatch 에서 승자가 대결 아이템이 아니면 예외가 발생한다`() {
        val tournamentId = service.start(userId, StartTournament("토너먼트", round = 4, wishItemIds = (1L..4L).toList()))
        val match =
            RecordMatch(
                tournamentId = tournamentId,
                currentRound = 4,
                firstWishItemId = 1L,
                secondWishItemId = 2L,
                winnerWishItemId = 99L,
            )

        assertFailsWith<TournamentException> {
            service.recordMatch(userId, match)
        }
    }

    @Test
    fun `recordMatch 에서 이미 완료된 토너먼트면 예외가 발생한다`() {
        val tournamentId = service.start(userId, StartTournament("결승 토너먼트", round = 2, wishItemIds = listOf(10L, 20L)))
        val finalMatch =
            RecordMatch(
                tournamentId = tournamentId,
                currentRound = 2,
                firstWishItemId = 10L,
                secondWishItemId = 20L,
                winnerWishItemId = 10L,
            )
        service.recordMatch(userId, finalMatch)

        assertFailsWith<TournamentException> {
            service.recordMatch(userId, finalMatch)
        }
    }

    @Test
    fun `getTournamentById 는 토너먼트 정보와 히스토리를 반환한다`() {
        val tournamentId = service.start(userId, StartTournament("조회 토너먼트", round = 4, wishItemIds = (1L..4L).toList()))
        service.recordMatch(
            userId,
            RecordMatch(
                tournamentId = tournamentId,
                currentRound = 4,
                firstWishItemId = 1L,
                secondWishItemId = 2L,
                winnerWishItemId = 2L,
            ),
        )

        val info = service.getTournamentById(tournamentId, userId)

        assertEquals(tournamentId, info.tournamentId)
        assertNull(info.finalWinnerWishItemId)
        assertEquals(1, info.history.size)
        assertEquals(2L, info.history[0].winnerWishItemId)
    }

    @Test
    fun `getTournamentById 는 히스토리가 없어도 빈 리스트로 반환한다`() {
        val tournamentId = service.start(userId, StartTournament("빈 토너먼트", round = 4, wishItemIds = (1L..4L).toList()))

        val info = service.getTournamentById(tournamentId, userId)

        assertEquals(tournamentId, info.tournamentId)
        assertEquals(0, info.history.size)
    }

    @Test
    fun `getTournamentById 에서 존재하지 않는 tournamentId 면 예외가 발생한다`() {
        assertFailsWith<TournamentException> {
            service.getTournamentById(999L, userId)
        }
    }

    @Test
    fun `getTournamentById 에서 다른 사용자의 토너먼트면 예외가 발생한다`() {
        val tournamentId = service.start(userId, StartTournament("내 토너먼트", round = 4, wishItemIds = (1L..4L).toList()))

        assertFailsWith<TournamentException> {
            service.getTournamentById(tournamentId, otherUserId)
        }
    }

    @Test
    fun `start 에서 위시 아이템이 요청자의 것이 아니면 예외가 발생한다`() {
        val serviceWithNoOwnership = TournamentService(repository, TestWishRepository(allOwned = false))

        assertFailsWith<WishException> {
            serviceWithNoOwnership.start(userId, StartTournament("토너먼트", round = 4, wishItemIds = (1L..4L).toList()))
        }
    }

    @Test
    fun `start 에서 round 와 wishItemIds 크기가 다르면 IllegalArgumentException 이 발생한다`() {
        assertFailsWith<IllegalArgumentException> {
            service.start(userId, StartTournament("토너먼트", round = 8, wishItemIds = (1L..4L).toList()))
        }
    }

    @Test
    fun `getTournamentById 는 응답에 round 를 포함한다`() {
        val tournamentId = service.start(userId, StartTournament("조회 토너먼트", round = 4, wishItemIds = (1L..4L).toList()))

        val info = service.getTournamentById(tournamentId, userId)

        assertEquals(4, info.round)
    }
}
