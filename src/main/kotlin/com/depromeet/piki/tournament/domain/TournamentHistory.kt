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
    // 어느 참여자(TournamentUser)의 매치인지 식별한다. 참여자별 독립 진행에 필요.
    // nullable: 컬럼 추가 전 기존 행은 null. 신규 행은 항상 값을 가져야 한다.
    @Column(name = "tournament_user_id")
    val tournamentUserId: Long?,
    @Column(name = "current_round", nullable = false)
    val currentRound: Int,
    @Column(name = "first_tournament_item_id", nullable = false)
    val firstTournamentItemId: Long,
    @Column(name = "second_tournament_item_id", nullable = false)
    val secondTournamentItemId: Long,
    @Column(name = "selected_tournament_item_id", nullable = false)
    val selectedTournamentItemId: Long,
) : LongBaseEntity() {
    init {
        requireNotNull(tournamentUserId) { "tournamentUserId 는 반드시 있어야 한다" }
    }

    fun softDelete() {
        deletedAt = LocalDateTime.now()
    }
}
