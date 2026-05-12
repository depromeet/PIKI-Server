package com.depromeet.team3.tournament.repository

import com.depromeet.team3.tournament.domain.Tournament
import org.springframework.data.jpa.repository.JpaRepository

interface TournamentJpaRepository : JpaRepository<Tournament, Long>
