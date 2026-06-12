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
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    val userId: UUID,
    // 출전 시점 고정 snapshot 참조. raw Long(FK 없음). 위시 갱신과 무관하게 고정돼 토너먼트 공정성을 지킨다.
    // item 정체성은 snapshot.itemId 단일 출처 — tournament_item 은 itemId 를 따로 들지 않고 snapshot 으로 도달한다.
    @Column(name = "snapshot_id", nullable = false)
    val snapshotId: Long,
) : LongBaseEntity() {
    // 엔티티 불변식 — 0·음수는 존재할 수 없는 참조다. 정상 흐름에선 닿지 않고, 닿으면 코드 버그.
    init {
        require(tournamentId > 0) { "tournamentId 는 양수여야 한다: $tournamentId" }
        require(snapshotId > 0) { "snapshotId 는 양수여야 한다: $snapshotId" }
    }

    fun softDelete() {
        deletedAt = LocalDateTime.now()
    }
}
