package com.depromeet.team3.tournament.repository

import com.depromeet.team3.tournament.domain.TournamentUser
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class TournamentUserRepositoryImpl(
    private val tournamentUserJpaRepository: TournamentUserJpaRepository,
) : TournamentUserRepository {
    override fun save(tournamentUser: TournamentUser): TournamentUser = tournamentUserJpaRepository.save(tournamentUser)

    override fun findByTournamentIdAndUserId(
        tournamentId: Long,
        userId: UUID,
    ): TournamentUser? = tournamentUserJpaRepository.findByTournamentIdAndUserId(tournamentId, userId)
}
