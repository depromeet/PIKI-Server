package com.depromeet.team3.tournament.repository

import com.depromeet.team3.tournament.domain.TournamentItem
import org.springframework.data.jpa.repository.JpaRepository

interface TournamentItemJpaRepository : JpaRepository<TournamentItem, Long>
