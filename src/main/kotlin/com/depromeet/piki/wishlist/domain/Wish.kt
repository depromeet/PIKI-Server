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
    @Column(name = "item_id", nullable = false)
    val itemId: Long,
    // 활성 snapshot(현재 보여줄 버전) 참조. raw Long(FK 없음). 5단계 갱신에서 새 snapshot 으로 스왑된다.
    // nullable — 3단계 전환기에 추가됐고 기존 dev 데이터는 비우고 가므로 신규 등록부터 채워진다.
    @Column(name = "snapshot_id")
    val snapshotId: Long? = null,
) : LongBaseEntity() {
    // 소유자가 아니면 거부. 도메인이 자기방어해 어느 통로로 호출되든 같은 결과를 낸다.
    fun verifyOwnedBy(userId: UUID) {
        if (this.userId != userId) throw WishException.forbiddenWishItems()
    }

    // soft delete — 행을 지우지 않고 deletedAt 으로 마킹한다. 조회는 deletedAt IS NULL 만 본다.
    fun delete() {
        deletedAt = LocalDateTime.now()
    }
}
