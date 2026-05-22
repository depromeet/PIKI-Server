package com.depromeet.team3.tournament.repository

import com.depromeet.team3.tournament.domain.Tournament
import com.depromeet.team3.tournament.domain.TournamentStatus
import org.springframework.data.jpa.repository.JpaRepository

interface TournamentJpaRepository : JpaRepository<Tournament, Long> {
    fun findByIdInAndStatusOrderByUpdatedAtDesc(ids: Collection<Long>, status: TournamentStatus): List<Tournament>

    fun findByIdInOrderByUpdatedAtDesc(ids: Collection<Long>): List<Tournament>
}
