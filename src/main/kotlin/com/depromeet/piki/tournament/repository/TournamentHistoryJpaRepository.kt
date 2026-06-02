package com.depromeet.piki.tournament.repository

import com.depromeet.piki.tournament.domain.TournamentHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TournamentHistoryJpaRepository : JpaRepository<TournamentHistory, Long> {
    fun findAllByTournamentIdOrderByCurrentRoundAscIdAsc(tournamentId: Long): List<TournamentHistory>

    @Modifying
    @Query("DELETE FROM TournamentHistory h WHERE h.tournamentId = :tournamentId")
    fun deleteAllByTournamentId(@Param("tournamentId") tournamentId: Long)
}
