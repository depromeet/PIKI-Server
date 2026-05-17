package com.depromeet.team3.tournament.repository

import com.depromeet.team3.tournament.domain.TournamentUser

interface TournamentUserRepository {
    fun save(tournamentUser: TournamentUser): TournamentUser
    fun findById(id: Long): TournamentUser?
}
