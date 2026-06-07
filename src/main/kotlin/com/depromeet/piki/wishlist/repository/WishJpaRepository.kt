package com.depromeet.piki.wishlist.repository

import com.depromeet.piki.wishlist.domain.Wish
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
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

    // 탈퇴 cascade — 그 유저의 위시를 영구 하드삭제. 위시는 다른 데이터가 참조하지 않아 즉시 파기 가능. 멱등(없으면 0건).
    @Modifying
    @Query("DELETE FROM Wish w WHERE w.userId = :userId")
    fun hardDeleteAllByUserId(
        @Param("userId") userId: UUID,
    ): Int
}
