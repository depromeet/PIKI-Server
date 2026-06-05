package com.depromeet.piki.item.repository

import com.depromeet.piki.item.domain.ItemSnapshot
import org.springframework.data.jpa.repository.JpaRepository

interface ItemSnapshotJpaRepository : JpaRepository<ItemSnapshot, Long> {
    // 한 item 의 살아있는(soft-delete 안 된) 최신 snapshot 1개 — id 역순 첫 행.
    fun findFirstByItemIdAndDeletedAtIsNullOrderByIdDesc(itemId: Long): ItemSnapshot?

    // 살아있는 단건 조회. JpaRepository.findById(Optional) 와 충돌하지 않도록 deletedAt 조건을 붙여 이름을 구분한다.
    fun findByIdAndDeletedAtIsNull(id: Long): ItemSnapshot?

    // 살아있는 행만 id 목록으로 일괄 조회.
    fun findByIdInAndDeletedAtIsNull(ids: Collection<Long>): List<ItemSnapshot>
}
