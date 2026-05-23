package com.depromeet.piki.tournament.repository

import com.depromeet.piki.tournament.domain.Tournament
import com.depromeet.piki.tournament.domain.TournamentStatus
import org.springframework.data.jpa.repository.JpaRepository

interface TournamentJpaRepository : JpaRepository<Tournament, Long> {
    fun findByIdInAndStatusInOrderByCreatedAtDesc(ids: List<Long>, statuses: List<TournamentStatus>): List<Tournament>

    fun findByIdInOrderByCreatedAtDesc(ids: List<Long>): List<Tournament>
}
