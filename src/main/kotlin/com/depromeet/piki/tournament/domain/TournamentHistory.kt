package com.depromeet.piki.tournament.domain

import com.depromeet.piki.common.domain.LongBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "tournament_histories")
class TournamentHistory(
    @Column(name = "tournament_id", nullable = false)
    val tournamentId: Long,
    @Column(name = "current_round", nullable = false)
    val currentRound: Int,
    @Column(name = "first_tournament_item_id", nullable = false)
    val firstTournamentItemId: Long,
    @Column(name = "second_tournament_item_id", nullable = false)
    val secondTournamentItemId: Long,
    @Column(name = "selected_tournament_item_id", nullable = false)
    val selectedTournamentItemId: Long,
) : LongBaseEntity() {
    fun softDelete() {
        deletedAt = LocalDateTime.now()
    }
}
