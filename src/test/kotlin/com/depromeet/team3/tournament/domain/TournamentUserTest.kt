package com.depromeet.team3.tournament.domain

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TournamentUserTest {
    @Test
    fun `정상 ID 로 생성되면 필드가 그대로 보존된다`() {
        val userId = UUID.randomUUID()

        val tournamentUser = TournamentUser(tournamentId = 1, userId = userId)

        assertEquals(1, tournamentUser.tournamentId)
        assertEquals(userId, tournamentUser.userId)
    }

    @Test
    fun `tournamentId 가 0 이하면 생성이 거부된다`() {
        assertFailsWith<IllegalArgumentException> {
            TournamentUser(tournamentId = 0, userId = UUID.randomUUID())
        }
    }
}
