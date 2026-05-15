package com.depromeet.team3.wishlist.repository

import com.depromeet.team3.product.domain.ProductLink
import com.depromeet.team3.wishlist.domain.Wish
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class WishRepositoryImpl(
    private val wishJpaRepository: WishJpaRepository,
) : WishRepository {
    override fun save(wish: Wish): Wish = wishJpaRepository.save(wish)

    override fun existsByUserIdAndProductLink(
        userId: UUID,
        link: ProductLink,
    ): Boolean = wishJpaRepository.existsByUserIdAndProductLink(userId, link)

    override fun countByIdsAndUserId(
        ids: List<Long>,
        userId: UUID,
    ): Long = wishJpaRepository.countByIdInAndUserId(ids, userId)
}
