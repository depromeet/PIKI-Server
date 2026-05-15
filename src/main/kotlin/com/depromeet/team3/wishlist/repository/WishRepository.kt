package com.depromeet.team3.wishlist.repository

import com.depromeet.team3.product.domain.ProductLink
import com.depromeet.team3.wishlist.domain.Wish
import java.util.UUID

interface WishRepository {
    fun save(wish: Wish): Wish

    fun existsByUserIdAndProductLink(
        userId: UUID,
        link: ProductLink,
    ): Boolean

    fun countByIdsAndUserId(
        ids: List<Long>,
        userId: UUID,
    ): Long
}
