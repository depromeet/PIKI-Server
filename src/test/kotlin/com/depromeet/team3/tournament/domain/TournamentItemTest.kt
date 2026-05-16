package com.depromeet.team3.tournament.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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

    @ParameterizedTest
    @ValueSource(longs = [0, -1])
    fun `tournamentId 가 양수가 아니면 생성이 거부된다`(invalidId: Long) {
        assertFailsWith<IllegalArgumentException> {
            TournamentItem(tournamentId = invalidId, itemId = 1, userId = UUID.randomUUID())
        }
    }

    @ParameterizedTest
    @ValueSource(longs = [0, -1])
    fun `itemId 가 양수가 아니면 생성이 거부된다`(invalidId: Long) {
        assertFailsWith<IllegalArgumentException> {
            TournamentItem(tournamentId = 1, itemId = invalidId, userId = UUID.randomUUID())
        }
    }
}
