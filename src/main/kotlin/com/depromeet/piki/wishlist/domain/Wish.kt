package com.depromeet.piki.wishlist.domain

import com.depromeet.piki.common.domain.LongBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "wishes")
class Wish(
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    val userId: UUID,
    // 활성 snapshot(현재 보여줄 버전) 참조. raw Long(FK 없음). 5단계 갱신에서 새 snapshot 으로 스왑된다.
    // item 정체성은 snapshot.itemId 단일 출처 — wish 는 itemId 를 따로 들지 않고 snapshot 으로 도달한다.
    @Column(name = "snapshot_id", nullable = false)
    val snapshotId: Long,
) : LongBaseEntity() {
    // 엔티티 불변식 — 0·음수는 존재할 수 없는 참조다. 정상 흐름에선 닿지 않고, 닿으면 코드 버그.
    init {
        require(snapshotId > 0) { "snapshotId 는 양수여야 한다: $snapshotId" }
    }

    // 소유자가 아니면 거부. 도메인이 자기방어해 어느 통로로 호출되든 같은 결과를 낸다.
    fun verifyOwnedBy(userId: UUID) {
        if (this.userId != userId) throw WishException.forbiddenWishItems()
    }

    // soft delete — 행을 지우지 않고 deletedAt 으로 마킹한다. 조회는 deletedAt IS NULL 만 본다.
    fun delete() {
        deletedAt = LocalDateTime.now()
    }
}
