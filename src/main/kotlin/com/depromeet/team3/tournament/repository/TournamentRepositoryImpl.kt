package com.depromeet.team3.tournament.repository

import com.depromeet.team3.tournament.domain.Tournament
import com.depromeet.team3.tournament.domain.TournamentHistory
import com.depromeet.team3.tournament.domain.TournamentStatus
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class TournamentRepositoryImpl(
    private val tournamentJpaRepository: TournamentJpaRepository,
    private val tournamentHistoryJpaRepository: TournamentHistoryJpaRepository,
) : TournamentRepository {
    override fun saveTournament(tournament: Tournament): Tournament = tournamentJpaRepository.save(tournament)

    override fun saveHistory(history: TournamentHistory) {
        tournamentHistoryJpaRepository.save(history)
    }

    override fun findTournamentById(tournamentId: Long): Tournament? =
        tournamentJpaRepository.findByIdOrNull(tournamentId)

    override fun findTournamentHistoriesByTournamentId(tournamentId: Long): List<TournamentHistory> =
        tournamentHistoryJpaRepository.findAllByTournamentIdOrderByCurrentRoundAscIdAsc(tournamentId)

    override fun findByIdsAndStatus(ids: List<Long>, status: TournamentStatus?): List<Tournament> {
        if (ids.isEmpty()) return emptyList()
        return status?.let { tournamentJpaRepository.findByIdInAndStatusOrderByUpdatedAtDesc(ids, it) }
            ?: tournamentJpaRepository.findByIdInOrderByUpdatedAtDesc(ids)
    }
}
