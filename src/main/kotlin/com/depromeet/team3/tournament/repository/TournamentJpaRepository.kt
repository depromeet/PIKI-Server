package com.depromeet.team3.tournament.repository

import com.depromeet.team3.tournament.domain.Tournament
import com.depromeet.team3.tournament.domain.TournamentStatus
import org.springframework.data.jpa.repository.JpaRepository

interface TournamentJpaRepository : JpaRepository<Tournament, Long> {
    fun findByIdInAndStatusInOrderByCreatedAtDesc(ids: List<Long>, statuses: List<TournamentStatus>): List<Tournament>

    fun findByIdInOrderByCreatedAtDesc(ids: List<Long>): List<Tournament>
}
