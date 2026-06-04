package com.depromeet.piki.item.repository

import com.depromeet.piki.item.domain.ItemSnapshot
import org.springframework.data.jpa.repository.JpaRepository

interface ItemSnapshotJpaRepository : JpaRepository<ItemSnapshot, Long> {
    // 한 item 의 살아있는(soft-delete 안 된) 최신 snapshot 1개 — id 역순 첫 행.
    fun findFirstByItemIdAndDeletedAtIsNullOrderByIdDesc(itemId: Long): ItemSnapshot?
}
