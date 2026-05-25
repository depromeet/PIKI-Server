package com.depromeet.piki.tournament.repository

import com.depromeet.piki.tournament.domain.TournamentUser
import java.util.UUID

interface TournamentUserRepository {
    fun save(tournamentUser: TournamentUser): TournamentUser

    fun findByTournamentIdAndUserId(
        tournamentId: Long,
        userId: UUID,
    ): TournamentUser?

    fun findTournamentIdsByUserId(userId: UUID): List<Long>

    fun findByTournamentIds(tournamentIds: List<Long>): List<TournamentUser>
}
