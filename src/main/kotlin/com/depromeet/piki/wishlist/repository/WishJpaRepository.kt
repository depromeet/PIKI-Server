package com.depromeet.piki.wishlist.repository

import com.depromeet.piki.wishlist.domain.Wish
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
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

    // 탈퇴 cascade — 그 유저의 활성 위시를 일괄 soft-delete. 이미 삭제된 행은 건드리지 않아 멱등하다.
    @Modifying
    @Query("UPDATE Wish w SET w.deletedAt = :now WHERE w.userId = :userId AND w.deletedAt IS NULL")
    fun softDeleteAllByUserId(
        @Param("userId") userId: UUID,
        @Param("now") now: LocalDateTime,
    ): Int

    // 30일 파기 — soft-delete 된 위시를 영구 하드삭제. tombstone 유저의 콘텐츠 잔재 제거.
    @Modifying
    @Query("DELETE FROM Wish w WHERE w.userId = :userId")
    fun hardDeleteAllByUserId(
        @Param("userId") userId: UUID,
    ): Int
}
