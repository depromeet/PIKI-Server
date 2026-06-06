package com.depromeet.piki.tournament.repository

import com.depromeet.piki.tournament.domain.Tournament
import com.depromeet.piki.tournament.domain.TournamentHistory
import com.depromeet.piki.tournament.domain.TournamentStatus

interface TournamentRepository {
    fun saveTournament(tournament: Tournament): Tournament

    fun saveHistory(history: TournamentHistory)

    fun findTournamentById(tournamentId: Long): Tournament?

    fun findTournamentByIdForUpdate(tournamentId: Long): Tournament?

    fun findTournamentHistoriesByTournamentId(tournamentId: Long): List<TournamentHistory>

    fun findHistoriesByTournamentIds(ids: List<Long>): List<TournamentHistory>

    fun findByIdsAndStatuses(
        ids: List<Long>,
        statuses: List<TournamentStatus>?,
    ): List<Tournament>

    fun softDeleteHistoriesByTournamentId(tournamentId: Long)

    fun findBySourceTournamentId(sourceTournamentId: Long): List<Tournament>

    fun findTournamentByInviteCode(code: String): Tournament?

    fun existsTournamentByInviteCode(code: String): Boolean
}
