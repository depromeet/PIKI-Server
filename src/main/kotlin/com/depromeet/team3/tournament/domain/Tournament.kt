package com.depromeet.team3.tournament.domain

import com.depromeet.team3.common.domain.LongBaseEntity
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import java.util.UUID

@Entity
class Tournament(
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    val userId: UUID,
    val name: String,
    val round: Int,
    @ElementCollection
    @CollectionTable(name = "tournament_wish_item", joinColumns = [JoinColumn(name = "tournament_id")])
    @Column(name = "wish_item_id")
    @OrderColumn(name = "position")
    val wishItemIds: List<Long>,
    var finalWinnerWishItemId: Long? = null,
    @Enumerated(value = EnumType.STRING)
    @Column(columnDefinition = "varchar(50)")
    var status: TournamentStatus = TournamentStatus.IN_PROGRESS,
) : LongBaseEntity() {
    init {
        require(wishItemIds.size == round) {
            "토너먼트 참가 아이템 수(${wishItemIds.size})가 라운드 수($round)와 다릅니다."
        }
    }

    fun complete(winnerWishItemId: Long) {
        check(!isCompleted()) { "이미 완료된 토너먼트입니다." }
        require(winnerWishItemId in wishItemIds) { "우승자는 토너먼트 참가 아이템 중 하나여야 합니다." }
        this.finalWinnerWishItemId = winnerWishItemId
        this.status = TournamentStatus.COMPLETED
    }

    fun isFinalRound(currentRound: Int): Boolean = currentRound == FINAL_ROUND_SIZE

    fun isCompleted(): Boolean = status == TournamentStatus.COMPLETED

    companion object {
        private const val FINAL_ROUND_SIZE = 2
    }
}
