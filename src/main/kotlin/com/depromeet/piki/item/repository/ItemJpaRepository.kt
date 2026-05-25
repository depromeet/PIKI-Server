package com.depromeet.piki.item.repository

import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.item.domain.ItemStatus
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
}
