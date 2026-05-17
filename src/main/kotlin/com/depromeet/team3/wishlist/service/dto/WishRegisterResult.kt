package com.depromeet.team3.wishlist.service.dto

import com.depromeet.team3.item.domain.Item
import com.depromeet.team3.wishlist.domain.Wish

data class WishRegisterResult(
    val wish: Wish,
    val item: Item,
)
