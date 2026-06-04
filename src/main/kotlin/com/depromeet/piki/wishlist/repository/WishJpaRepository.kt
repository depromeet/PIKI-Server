package com.depromeet.piki.wishlist.repository

import com.depromeet.piki.wishlist.domain.Wish
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WishJpaRepository : JpaRepository<Wish, Long> {
    fun countByIdInAndUserId(
        ids: Collection<Long>,
        userId: UUID,
    ): Long

    fun countByItemIdInAndUserIdAndDeletedAtIsNull(
        itemIds: Collection<Long>,
        userId: UUID,
    ): Long

    fun findByUserIdAndDeletedAtIsNullOrderByIdDesc(
        userId: UUID,
        limit: Limit,
    ): List<Wish>

    fun findByUserIdAndIdLessThanAndDeletedAtIsNullOrderByIdDesc(
        userId: UUID,
        id: Long,
        limit: Limit,
    ): List<Wish>

    fun findByIdAndDeletedAtIsNull(id: Long): Wish?

    fun findByIdInAndDeletedAtIsNull(ids: Collection<Long>): List<Wish>

    fun findByItemIdInAndUserIdAndDeletedAtIsNull(
        itemIds: Collection<Long>,
        userId: UUID,
    ): List<Wish>
}
