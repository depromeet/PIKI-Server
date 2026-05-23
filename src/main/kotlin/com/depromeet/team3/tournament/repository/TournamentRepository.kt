package com.depromeet.team3.tournament.repository

import com.depromeet.team3.tournament.domain.Tournament
import com.depromeet.team3.tournament.domain.TournamentHistory
import com.depromeet.team3.tournament.domain.TournamentStatus

interface TournamentRepository {
    fun saveTournament(tournament: Tournament): Tournament

    fun saveHistory(history: TournamentHistory)

    fun findTournamentById(tournamentId: Long): Tournament?

    fun findTournamentHistoriesByTournamentId(tournamentId: Long): List<TournamentHistory>

    fun findByIdsAndStatuses(ids: List<Long>, statuses: List<TournamentStatus>?): List<Tournament>
}
