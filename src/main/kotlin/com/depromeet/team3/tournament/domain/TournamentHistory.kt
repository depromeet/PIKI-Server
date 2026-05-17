package com.depromeet.team3.tournament.domain

import com.depromeet.team3.common.domain.LongBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity

@Entity
class TournamentHistory(
    @Column(name = "tournament_id", nullable = false)
    val tournamentId: Long,
    val currentRound: Int,
    @Column(name = "first_tournament_item_id", nullable = false)
    val firstTournamentItemId: Long,
    @Column(name = "second_tournament_item_id", nullable = false)
    val secondTournamentItemId: Long,
    @Column(name = "selected_tournament_item_id", nullable = false)
    val selectedTournamentItemId: Long,
) : LongBaseEntity()
