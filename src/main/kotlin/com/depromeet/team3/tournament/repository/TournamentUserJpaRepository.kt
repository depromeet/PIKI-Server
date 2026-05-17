package com.depromeet.team3.tournament.repository

import com.depromeet.team3.tournament.domain.TournamentUser
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TournamentUserJpaRepository : JpaRepository<TournamentUser, Long> {
    fun findByTournamentIdAndUserId(
        tournamentId: Long,
        userId: UUID,
    ): TournamentUser?
}
