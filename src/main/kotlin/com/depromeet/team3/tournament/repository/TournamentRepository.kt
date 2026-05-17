package com.depromeet.team3.tournament.repository

import com.depromeet.team3.tournament.domain.Tournament
import com.depromeet.team3.tournament.domain.TournamentHistory

interface TournamentRepository {
    fun saveTournament(tournament: Tournament): Tournament
    fun saveHistory(history: TournamentHistory)

    fun findTournamentById(tournamentId: Long): Tournament?
    fun findTournamentHistoriesByTournamentId(tournamentId: Long): List<TournamentHistory>
}
