package com.depromeet.piki.tournament.repository

import com.depromeet.piki.tournament.domain.Tournament
import com.depromeet.piki.tournament.domain.TournamentHistory
import com.depromeet.piki.tournament.domain.TournamentStatus
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
        tournamentJpaRepository.findByIdAndDeletedAtIsNull(tournamentId)

    override fun findTournamentByIdForUpdate(tournamentId: Long): Tournament? =
        tournamentJpaRepository.findByIdForUpdate(tournamentId)

    override fun findTournamentHistoriesByTournamentId(tournamentId: Long): List<TournamentHistory> =
        tournamentHistoryJpaRepository.findAllByTournamentIdOrderByCurrentRoundAscIdAsc(tournamentId)

    override fun findByIdsAndStatuses(
        ids: List<Long>,
        statuses: List<TournamentStatus>?,
    ): List<Tournament> {
        if (ids.isEmpty()) return emptyList()
        return statuses
            ?.takeIf { it.isNotEmpty() }
            ?.let { tournamentJpaRepository.findByIdInAndStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(ids, it) }
            ?: tournamentJpaRepository.findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(ids)
    }
}
