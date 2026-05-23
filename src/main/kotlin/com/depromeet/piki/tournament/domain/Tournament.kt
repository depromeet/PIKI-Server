package com.depromeet.piki.tournament.domain

import com.depromeet.piki.common.domain.LongBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "tournaments")
class Tournament(
    ownerTournamentUserId: Long,
    @Column(name = "name", nullable = false)
    val name: String,
    @Enumerated(value = EnumType.STRING)
    @Column(columnDefinition = "varchar(50)")
    var status: TournamentStatus = TournamentStatus.PENDING,
) : LongBaseEntity() {
    // open class에서 private set이 금지되므로 backing field로 캡슐화한다.
    // Hibernate는 field access로 직접 접근하고, 외부에서는 getter만 노출된다.
    @Column(name = "owner_tournament_user_id", nullable = false)
    private var _ownerTournamentUserId: Long = ownerTournamentUserId

    val ownerTournamentUserId: Long get() = _ownerTournamentUserId

    fun assignOwner(tournamentUserId: Long) {
        _ownerTournamentUserId = tournamentUserId
    }

    fun start() {
        check(isPending()) { "start는 PENDING 상태에서만 호출 가능" }
        this.status = TournamentStatus.IN_PROGRESS
    }

    fun complete() {
        check(isInProgress()) { "complete는 IN_PROGRESS 상태에서만 호출 가능" }
        this.status = TournamentStatus.COMPLETED
    }

    fun isFinalRound(currentRound: Int): Boolean = currentRound == FINAL_ROUND_SIZE

    fun isPending(): Boolean = status == TournamentStatus.PENDING

    fun isInProgress(): Boolean = status == TournamentStatus.IN_PROGRESS

    companion object {
        private const val FINAL_ROUND_SIZE = 2
    }
}
