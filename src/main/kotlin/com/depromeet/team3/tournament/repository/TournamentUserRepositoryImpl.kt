package com.depromeet.team3.tournament.repository

import com.depromeet.team3.tournament.domain.TournamentUser
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class TournamentUserRepositoryImpl(
    private val tournamentUserJpaRepository: TournamentUserJpaRepository,
) : TournamentUserRepository {
    override fun save(tournamentUser: TournamentUser): TournamentUser =
        tournamentUserJpaRepository.save(tournamentUser)

    override fun findById(id: Long): TournamentUser? =
        tournamentUserJpaRepository.findByIdOrNull(id)
}
