package com.depromeet.team3.wishlist.repository

import com.depromeet.team3.wishlist.domain.Wish
import com.depromeet.team3.wishlist.domain.WishCursor
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

    override fun findPage(
        userId: UUID,
        cursor: WishCursor?,
        limit: Int,
    ): List<Wish> {
        val limited = Limit.of(limit)
        cursor ?: return wishJpaRepository.findByUserIdOrderByIdDesc(userId, limited)
        return wishJpaRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, cursor.lastWishId, limited)
    }
}
