package com.depromeet.piki.wishlist.repository

import com.depromeet.piki.wishlist.domain.Wish
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface WishJpaRepository : JpaRepository<Wish, Long> {
    // 이 아이템을 위시에 담은 유저들. 같은 아이템을 여러 유저가 담을 수 있어 DISTINCT. (알림 수신자 역조회)
    @Query("SELECT DISTINCT w.userId FROM Wish w WHERE w.itemId = :itemId AND w.deletedAt IS NULL")
    fun findUserIdsByItemId(
        @Param("itemId") itemId: Long,
    ): List<UUID>

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
