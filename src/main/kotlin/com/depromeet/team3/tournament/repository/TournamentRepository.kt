package com.depromeet.team3.tournament.repository

import com.depromeet.team3.tournament.domain.Tournament
import com.depromeet.team3.tournament.domain.TournamentHistory
import com.depromeet.team3.tournament.domain.TournamentItem

interface TournamentRepository {
    fun saveTournament(tournament: Tournament): Long
    fun saveTournamentItems(items: List<TournamentItem>): List<TournamentItem>
    fun saveHistory(history: TournamentHistory)

    fun findTournamentById(tournamentId: Long): Tournament?
    fun findTournamentItemsByTournamentId(tournamentId: Long): List<TournamentItem>
    fun findTournamentHistoriesByTournamentId(tournamentId: Long): List<TournamentHistory>
}
