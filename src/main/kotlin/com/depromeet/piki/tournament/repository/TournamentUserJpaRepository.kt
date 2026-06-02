package com.depromeet.piki.tournament.repository

import com.depromeet.piki.tournament.domain.TournamentUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface TournamentUserJpaRepository : JpaRepository<TournamentUser, Long> {
    fun findByTournamentIdAndUserId(
        tournamentId: Long,
        userId: UUID,
    ): TournamentUser?

    @Query("SELECT tu.tournamentId FROM TournamentUser tu WHERE tu.userId = :userId")
    fun findTournamentIdsByUserId(
        @Param("userId") userId: UUID,
    ): List<Long>

    fun findByTournamentId(tournamentId: Long): List<TournamentUser>

    fun findByTournamentIdIn(tournamentIds: Collection<Long>): List<TournamentUser>

    @Modifying
    @Query("DELETE FROM TournamentUser tu WHERE tu.tournamentId = :tournamentId")
    fun deleteAllByTournamentId(@Param("tournamentId") tournamentId: Long)
}
