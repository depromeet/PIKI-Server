package com.depromeet.piki.item.repository

import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.item.domain.ItemStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface ItemJpaRepository : JpaRepository<Item, Long> {
    @Query("select i.id from Item i where i.status = :status and i.createdAt < :cutoff and i.deletedAt is null")
    fun findIdsByStatusAndCreatedAtBefore(
        @Param("status") status: ItemStatus,
        @Param("cutoff") cutoff: LocalDateTime,
    ): List<Long>

    // admin 조회 도구용. 삭제되지 않은 item 을 createdAt 내림차순으로, limit 은 Pageable 로 동적 주입.
    @Query("select i from Item i where i.deletedAt is null order by i.createdAt desc")
    fun findRecent(pageable: Pageable): List<Item>
}
