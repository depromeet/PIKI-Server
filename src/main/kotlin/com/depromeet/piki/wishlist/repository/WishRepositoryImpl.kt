package com.depromeet.piki.wishlist.repository

import com.depromeet.piki.wishlist.domain.Wish
import com.depromeet.piki.wishlist.domain.WishCursor
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class WishRepositoryImpl(
    private val wishJpaRepository: WishJpaRepository,
) : WishRepository {
    override fun save(wish: Wish): Wish = wishJpaRepository.save(wish)

    override fun countByIdsAndUserId(
        ids: List<Long>,
        userId: UUID,
    ): Long = wishJpaRepository.countByIdInAndUserId(ids, userId)

    override fun countByItemIdsAndUserId(
        itemIds: List<Long>,
        userId: UUID,
    ): Long = wishJpaRepository.countByItemIdInAndUserIdAndDeletedAtIsNull(itemIds, userId)

    override fun findPage(
        userId: UUID,
        cursor: WishCursor?,
        limit: Int,
    ): List<Wish> {
        val limited = Limit.of(limit)
        cursor ?: return wishJpaRepository.findByUserIdAndDeletedAtIsNullOrderByIdDesc(userId, limited)
        return wishJpaRepository.findByUserIdAndIdLessThanAndDeletedAtIsNullOrderByIdDesc(
            userId,
            cursor.lastWishId,
            limited,
        )
    }

    override fun findById(id: Long): Wish? = wishJpaRepository.findByIdAndDeletedAtIsNull(id)
}
