package com.depromeet.piki.tournament.repository

import com.depromeet.piki.tournament.domain.TournamentItem
import com.depromeet.piki.tournament.domain.TournamentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TournamentItemJpaRepository : JpaRepository<TournamentItem, Long> {
    fun countByTournamentId(tournamentId: Long): Int

    fun findAllByTournamentIdOrderByIdAsc(tournamentId: Long): List<TournamentItem>

    @Query("SELECT t.id FROM TournamentItem t WHERE t.tournamentId = :tournamentId ORDER BY t.id ASC")
    fun findIdsByTournamentId(@Param("tournamentId") tournamentId: Long): List<Long>

    @Modifying
    @Query(
        "DELETE FROM TournamentItem t WHERE t.id = :id AND t.tournamentId = :tournamentId " +
            "AND EXISTS (SELECT 1 FROM Tournament tour WHERE tour.id = :tournamentId AND tour.status = :status)",
    )
    fun deleteIfPending(
        @Param("id") id: Long,
        @Param("tournamentId") tournamentId: Long,
        @Param("status") status: TournamentStatus = TournamentStatus.PENDING,
    ): Int

    @Modifying
    @Query("DELETE FROM TournamentItem t WHERE t.tournamentId = :tournamentId")
    fun deleteAllByTournamentId(@Param("tournamentId") tournamentId: Long)
}
