package com.depromeet.piki.tournament.domain

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

        val tournamentItem = TournamentItem(tournamentId = 1, userId = userId, snapshotId = 2)

        assertEquals(1, tournamentItem.tournamentId)
        assertEquals(2, tournamentItem.snapshotId)
        assertEquals(userId, tournamentItem.userId)
    }

    @ParameterizedTest
    @ValueSource(longs = [0, -1])
    fun `tournamentId 가 양수가 아니면 생성이 거부된다`(invalidId: Long) {
        assertFailsWith<IllegalArgumentException> {
            TournamentItem(tournamentId = invalidId, userId = UUID.randomUUID(), snapshotId = 1)
        }
    }

    @ParameterizedTest
    @ValueSource(longs = [0, -1])
    fun `snapshotId 가 양수가 아니면 생성이 거부된다`(invalidId: Long) {
        assertFailsWith<IllegalArgumentException> {
            TournamentItem(tournamentId = 1, userId = UUID.randomUUID(), snapshotId = invalidId)
        }
    }
}
