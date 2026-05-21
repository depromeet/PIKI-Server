package com.depromeet.team3.tournament.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TournamentTest {
    private fun tournament(status: TournamentStatus = TournamentStatus.PENDING) =
        Tournament(ownerTournamentUserId = 0L, name = "테스트").also {
            when (status) {
                TournamentStatus.IN_PROGRESS -> it.start()
                TournamentStatus.COMPLETED -> {
                    it.start()
                    it.complete()
                }
                else -> Unit
            }
        }

    @Test
    fun `기본 생성 시 PENDING 상태이다`() {
        val t = Tournament(ownerTournamentUserId = 0L, name = "테스트")
        assertTrue(t.isPending())
        assertFalse(t.isInProgress())
    }

    @Test
    fun `assignOwner 는 ownerTournamentUserId 를 변경한다`() {
        val t = Tournament(ownerTournamentUserId = 0L, name = "테스트")
        t.assignOwner(42L)
        assertEquals(42L, t.ownerTournamentUserId)
    }

    @Test
    fun `start 는 상태를 IN_PROGRESS 로 변경한다`() {
        val t = tournament()
        t.start()
        assertEquals(TournamentStatus.IN_PROGRESS, t.status)
        assertTrue(t.isInProgress())
        assertFalse(t.isPending())
    }

    @Test
    fun `complete 는 상태를 COMPLETED 로 변경한다`() {
        val t = tournament(TournamentStatus.IN_PROGRESS)
        t.complete()
        assertEquals(TournamentStatus.COMPLETED, t.status)
        assertFalse(t.isInProgress())
    }

    @Test
    fun `isFinalRound 는 currentRound 가 2 일 때 true 를 반환한다`() {
        val t = tournament()
        assertTrue(t.isFinalRound(2))
    }

    @Test
    fun `isFinalRound 는 currentRound 가 2 가 아닐 때 false 를 반환한다`() {
        val t = tournament()
        assertFalse(t.isFinalRound(4))
        assertFalse(t.isFinalRound(8))
        assertFalse(t.isFinalRound(1))
    }
}
