package com.depromeet.team3.tournament.repository

import com.depromeet.team3.tournament.domain.TournamentUser
import org.springframework.data.jpa.repository.JpaRepository

interface TournamentUserJpaRepository : JpaRepository<TournamentUser, Long>
