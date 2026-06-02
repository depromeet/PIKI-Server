package com.depromeet.piki.tournament.repository

import com.depromeet.piki.tournament.domain.TournamentItem

interface TournamentItemRepository {
    fun saveAll(items: List<TournamentItem>): List<TournamentItem>

    fun countByTournamentId(tournamentId: Long): Int

    fun findIdsByTournamentId(tournamentId: Long): List<Long>

    fun findAllByTournamentId(tournamentId: Long): List<TournamentItem>

    fun findByIds(ids: List<Long>): List<TournamentItem>

    fun findById(id: Long): TournamentItem?

    fun delete(tournamentItem: TournamentItem)

    fun deleteIfPending(
        id: Long,
        tournamentId: Long,
    ): Int

    fun deleteAllByTournamentId(tournamentId: Long)
}
