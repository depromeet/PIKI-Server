package com.depromeet.team3.tournament.domain

import com.depromeet.team3.common.domain.LongBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.util.UUID

@Entity
class Tournament(
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    val userId: UUID,
    val name: String,
    @Enumerated(value = EnumType.STRING)
    @Column(columnDefinition = "varchar(50)")
    var status: TournamentStatus = TournamentStatus.PENDING,
) : LongBaseEntity() {

    fun start() {
        check(status == TournamentStatus.PENDING) { "PENDING 상태인 토너먼트만 시작할 수 있습니다." }
        this.status = TournamentStatus.IN_PROGRESS
    }

    fun complete() {
        check(isInProgress()) { "진행 중인 토너먼트만 완료할 수 있습니다." }
        this.status = TournamentStatus.COMPLETED
    }

    fun isFinalRound(currentRound: Int): Boolean = currentRound == FINAL_ROUND_SIZE

    fun isInProgress(): Boolean = status == TournamentStatus.IN_PROGRESS

    fun isCompleted(): Boolean = status == TournamentStatus.COMPLETED

    companion object {
        private const val FINAL_ROUND_SIZE = 2
    }
}
