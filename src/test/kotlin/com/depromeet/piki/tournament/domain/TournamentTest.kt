package com.depromeet.piki.tournament.domain

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TournamentTest {
    private fun tournament(
        status: TournamentStatus = TournamentStatus.PENDING,
        inviteExpiresAt: LocalDateTime = LocalDateTime.now().plusMinutes(30),
    ) = Tournament(
        ownerTournamentUserId = 0L,
        name = "테스트",
        inviteCode = "ABC123",
        inviteExpiresAt = inviteExpiresAt,
    ).also {
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
        val t = tournament()
        assertTrue(t.isPending())
        assertFalse(t.isInProgress())
    }

    @Test
    fun `assignOwner 는 ownerTournamentUserId 를 변경한다`() {
        val t = tournament()
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

    @Test
    fun `isInviteValid 는 만료 시각이 현재보다 미래이면 true 를 반환한다`() {
        val t = tournament(inviteExpiresAt = LocalDateTime.now().plusSeconds(10))
        assertTrue(t.isInviteValid())
    }

    @Test
    fun `isInviteValid 는 만료 시각이 현재보다 과거이면 false 를 반환한다`() {
        val t = tournament(inviteExpiresAt = LocalDateTime.now().minusSeconds(1))
        assertFalse(t.isInviteValid())
    }

    @Test
    fun `generateInviteCode 는 대문자 영어 3자리 + 숫자 3자리 형식을 반환한다`() {
        val pattern = Regex("[A-Z]{3}\\d{3}")
        repeat(20) {
            val code = Tournament.generateInviteCode()
            assertTrue(code.matches(pattern), "expected [A-Z]{3}[0-9]{3} but got: $code")
            assertEquals(6, code.length)
        }
    }

    @Test
    fun `isPlayLinkValid 는 playLinkExpiresAt 이 null 이면 false 를 반환한다`() {
        val t = tournament()
        assertFalse(t.isPlayLinkValid())
    }

    @Test
    fun `isPlayLinkValid 는 만료 시각이 미래이면 true 를 반환한다`() {
        val t = tournament(TournamentStatus.COMPLETED)
        t.createPlayLink(LocalDateTime.now().plusDays(7))
        assertTrue(t.isPlayLinkValid())
    }

    @Test
    fun `isPlayLinkValid 는 만료 시각이 과거이면 false 를 반환한다`() {
        val t = tournament(TournamentStatus.COMPLETED)
        t.createPlayLink(LocalDateTime.now().minusSeconds(1))
        assertFalse(t.isPlayLinkValid())
    }

    @Test
    fun `isCompleted 는 COMPLETED 상태일 때만 true 를 반환한다`() {
        assertTrue(tournament(TournamentStatus.COMPLETED).isCompleted())
        assertFalse(tournament(TournamentStatus.PENDING).isCompleted())
        assertFalse(tournament(TournamentStatus.IN_PROGRESS).isCompleted())
    }
}
