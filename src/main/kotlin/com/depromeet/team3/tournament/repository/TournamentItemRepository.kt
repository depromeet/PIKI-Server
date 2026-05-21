package com.depromeet.team3.tournament.repository

import com.depromeet.team3.tournament.domain.TournamentItem

interface TournamentItemRepository {
    fun saveAll(items: List<TournamentItem>): List<TournamentItem>

    fun findAllByTournamentId(tournamentId: Long): List<TournamentItem>

    fun findById(id: Long): TournamentItem?

    fun delete(tournamentItem: TournamentItem)
}
