package com.depromeet.piki.tournament.repository

import com.depromeet.piki.tournament.domain.TournamentHistory
import org.springframework.data.jpa.repository.JpaRepository

interface TournamentHistoryJpaRepository : JpaRepository<TournamentHistory, Long> {
    fun findAllByTournamentIdOrderByCurrentRoundAscIdAsc(tournamentId: Long): List<TournamentHistory>
}
