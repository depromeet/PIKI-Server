package com.depromeet.piki.tournament.repository

import com.depromeet.piki.tournament.domain.Tournament
import com.depromeet.piki.tournament.domain.TournamentStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface TournamentJpaRepository : JpaRepository<Tournament, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Tournament?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tournament t WHERE t.id = :id AND t.deletedAt IS NULL")
    fun findByIdForUpdate(id: Long): Tournament?

    fun findByIdInAndStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(
        ids: List<Long>,
        statuses: List<TournamentStatus>,
    ): List<Tournament>

    fun findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(ids: List<Long>): List<Tournament>

    fun findBySourceTournamentIdAndDeletedAtIsNull(sourceTournamentId: Long): List<Tournament>

    fun findByInviteCodeAndDeletedAtIsNull(inviteCode: String): Tournament?
}
