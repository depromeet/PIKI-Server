package com.depromeet.piki.tournament.repository

import com.depromeet.piki.tournament.domain.Tournament
import com.depromeet.piki.tournament.domain.TournamentStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface TournamentJpaRepository : JpaRepository<Tournament, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tournament t WHERE t.id = :id")
    fun findByIdForUpdate(id: Long): Tournament?
    fun findByIdInAndStatusInOrderByCreatedAtDesc(
        ids: List<Long>,
        statuses: List<TournamentStatus>,
    ): List<Tournament>

    fun findByIdInOrderByCreatedAtDesc(ids: List<Long>): List<Tournament>
}
