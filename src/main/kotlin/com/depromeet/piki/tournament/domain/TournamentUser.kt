package com.depromeet.piki.tournament.domain

import com.depromeet.piki.common.domain.LongBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

// 어떤 유저가 어떤 토너먼트에 참여했는지를 명시 관리하는 매핑 테이블.
// userId 는 게스트·회원 모두 수용 (현재는 Guest 의 UUID).
@Entity
@Table(name = "tournament_users")
class TournamentUser(
    @Column(name = "tournament_id", nullable = false)
    val tournamentId: Long,
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    val userId: UUID,
) : LongBaseEntity() {
    // 엔티티 불변식 — 0·음수는 존재할 수 없는 참조다. 정상 흐름에선 닿지 않고, 닿으면 코드 버그.
    init {
        require(tournamentId > 0) { "tournamentId 는 양수여야 한다: $tournamentId" }
    }

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null

    fun complete() {
        completedAt = completedAt ?: LocalDateTime.now()
    }

    fun isCompleted() = completedAt?.let { true } ?: false

    fun softDelete() {
        deletedAt = LocalDateTime.now()
    }
}
