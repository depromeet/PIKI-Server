package com.depromeet.piki.tournament.domain

import com.depromeet.piki.common.domain.LongBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

// 토너먼트에 올라간 아이템. 게스트·회원이 추가한 item 을 토너먼트가 참조하는 연결 엔티티.
// userId 는 이 아이템을 토너먼트에 추가한 주체 (게스트·회원 모두 수용).
@Entity
@Table(name = "tournament_items")
class TournamentItem(
    @Column(name = "tournament_id", nullable = false)
    val tournamentId: Long,
    @Column(name = "item_id", nullable = false)
    val itemId: Long,
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    val userId: UUID,
    // 출전 시점 고정 snapshot 참조. raw Long(FK 없음). 위시 갱신과 무관하게 고정돼 토너먼트 공정성을 지킨다.
    // nullable — 3단계 전환기에 추가됐고, 신규 출전부터 채워진다.
    @Column(name = "snapshot_id")
    val snapshotId: Long? = null,
) : LongBaseEntity() {
    // 엔티티 불변식 — 0·음수는 존재할 수 없는 참조다. 정상 흐름에선 닿지 않고, 닿으면 코드 버그.
    init {
        require(tournamentId > 0) { "tournamentId 는 양수여야 한다: $tournamentId" }
        require(itemId > 0) { "itemId 는 양수여야 한다: $itemId" }
    }

    fun softDelete() {
        deletedAt = LocalDateTime.now()
    }
}
