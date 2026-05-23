package com.depromeet.team3.tournament.repository

import com.depromeet.team3.tournament.domain.TournamentUser
import org.springframework.data.jpa.repository.JpaRepository
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

    fun findByTournamentIdIn(tournamentIds: Collection<Long>): List<TournamentUser>
}
