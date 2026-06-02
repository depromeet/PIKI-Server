package com.depromeet.piki.tournament.repository

import com.depromeet.piki.tournament.domain.TournamentUser
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface TournamentUserJpaRepository : JpaRepository<TournamentUser, Long> {
    fun findByTournamentIdAndUserIdAndDeletedAtIsNull(
        tournamentId: Long,
        userId: UUID,
    ): TournamentUser?

    @Query("SELECT tu.tournamentId FROM TournamentUser tu WHERE tu.userId = :userId AND tu.deletedAt IS NULL")
    fun findTournamentIdsByUserId(
        @Param("userId") userId: UUID,
    ): List<Long>

    fun findByTournamentIdAndDeletedAtIsNull(tournamentId: Long): List<TournamentUser>

    @Query("SELECT tu FROM TournamentUser tu WHERE tu.tournamentId IN :tournamentIds AND tu.deletedAt IS NULL")
    fun findByTournamentIdInAndNotDeleted(
        @Param("tournamentIds") tournamentIds: Collection<Long>,
    ): List<TournamentUser>

    @Modifying
    @Query("UPDATE TournamentUser tu SET tu.deletedAt = :now WHERE tu.tournamentId = :tournamentId AND tu.deletedAt IS NULL")
    fun softDeleteAllByTournamentId(@Param("tournamentId") tournamentId: Long, @Param("now") now: LocalDateTime)
}
