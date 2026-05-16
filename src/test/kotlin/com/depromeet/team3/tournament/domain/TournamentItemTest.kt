package com.depromeet.team3.tournament.domain

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TournamentItemTest {
    @Test
    fun `정상 ID 로 생성되면 필드가 그대로 보존된다`() {
        val userId = UUID.randomUUID()

        val tournamentItem = TournamentItem(tournamentId = 1, itemId = 2, userId = userId)

        assertEquals(1, tournamentItem.tournamentId)
        assertEquals(2, tournamentItem.itemId)
        assertEquals(userId, tournamentItem.userId)
    }

    @Test
    fun `tournamentId 가 0 이하면 생성이 거부된다`() {
        assertFailsWith<IllegalArgumentException> {
            TournamentItem(tournamentId = 0, itemId = 1, userId = UUID.randomUUID())
        }
    }

    @Test
    fun `itemId 가 0 이하면 생성이 거부된다`() {
        assertFailsWith<IllegalArgumentException> {
            TournamentItem(tournamentId = 1, itemId = 0, userId = UUID.randomUUID())
        }
    }
}
