package com.depromeet.team3.wishlist.repository

import com.depromeet.team3.product.domain.ProductLink
import com.depromeet.team3.wishlist.domain.Wish
import java.util.UUID

interface WishRepository {
    fun save(wish: Wish): Wish

    fun existsByGuestIdAndProductLink(
        guestId: UUID,
        link: ProductLink,
    ): Boolean

    fun countByIdsAndGuestId(
        ids: List<Long>,
        guestId: UUID,
    ): Long
}
