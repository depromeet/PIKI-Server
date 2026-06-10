package com.depromeet.piki.tournament.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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

    @ParameterizedTest
    @ValueSource(longs = [0, -1])
    fun `tournamentId 가 양수가 아니면 생성이 거부된다`(invalidId: Long) {
        assertFailsWith<IllegalArgumentException> {
            TournamentUser(tournamentId = invalidId, userId = UUID.randomUUID())
        }
    }
}
