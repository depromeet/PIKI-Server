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

    override fun existsByGuestIdAndProductLink(
        guestId: UUID,
        link: ProductLink,
    ): Boolean = wishJpaRepository.existsByGuestIdAndProductLink(guestId, link)

    override fun countByIdsAndGuestId(
        ids: List<Long>,
        guestId: UUID,
    ): Long = wishJpaRepository.countByIdInAndGuestId(ids, guestId)
}
