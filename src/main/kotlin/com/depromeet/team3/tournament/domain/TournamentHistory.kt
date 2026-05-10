package com.depromeet.team3.tournament.domain

import com.depromeet.team3.common.domain.LongBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity

@Entity
class TournamentHistory(
    @Column(name = "tournament_id", nullable = false)
    val tournamentId: Long,
    val currentRound: Int,
    val firstItemId: Long,
    val secondItemId: Long,
    val winnerItemId: Long,
) : LongBaseEntity()
