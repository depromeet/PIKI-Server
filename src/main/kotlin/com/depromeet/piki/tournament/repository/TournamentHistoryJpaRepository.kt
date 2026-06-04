package com.depromeet.piki.tournament.repository

import com.depromeet.piki.tournament.domain.TournamentHistory
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TournamentHistoryJpaRepository : JpaRepository<TournamentHistory, Long> {
    fun findAllByTournamentIdAndDeletedAtIsNullOrderByCurrentRoundAscIdAsc(tournamentId: Long): List<TournamentHistory>

    @Modifying
    @Query("UPDATE TournamentHistory h SET h.deletedAt = :now WHERE h.tournamentId = :tournamentId AND h.deletedAt IS NULL")
    fun softDeleteAllByTournamentId(@Param("tournamentId") tournamentId: Long, @Param("now") now: LocalDateTime)
}
