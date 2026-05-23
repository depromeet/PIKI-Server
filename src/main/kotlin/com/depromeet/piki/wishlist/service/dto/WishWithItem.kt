package com.depromeet.piki.wishlist.service.dto

import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.wishlist.domain.Wish

// wish 기록과 그 wish 가 가리키는 item 스냅샷의 묶음. 등록 결과·조회 항목이 공유한다.
data class WishWithItem(
    val wish: Wish,
    val item: Item,
)
