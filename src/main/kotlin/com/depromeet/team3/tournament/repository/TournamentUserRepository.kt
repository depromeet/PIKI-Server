package com.depromeet.team3.tournament.repository

import com.depromeet.team3.tournament.domain.TournamentUser
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
