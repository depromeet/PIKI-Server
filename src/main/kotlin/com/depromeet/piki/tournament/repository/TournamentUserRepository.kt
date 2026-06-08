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

    fun findByTournamentId(tournamentId: Long): List<TournamentUser>

    fun countByTournamentId(tournamentId: Long): Int

    fun findByTournamentIds(tournamentIds: List<Long>): List<TournamentUser>

    fun findByIds(ids: Collection<Long>): List<TournamentUser>

    fun softDeleteAllByTournamentId(tournamentId: Long)

    fun softDeleteByTournamentIdAndUserId(tournamentId: Long, userId: UUID)
}
