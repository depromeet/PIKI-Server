package com.depromeet.piki.wishlist.service.dto

import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.item.domain.ItemSnapshot
import com.depromeet.piki.wishlist.domain.Wish

// 한 위시가 가리키는 상품(item)의 가격 히스토리. wish(활성 포인터)·item(정체성=link)과
// 그 item 의 추출 완료(READY) 버전 이력(history)을 묶는다. 활성 버전 식별은 wish.snapshotId 에서 컨트롤러가 읽는다.
// history 는 최신순(id desc)으로, 가격 없는 PENDING/PROCESSING/FAILED 는 제외돼 있다(repository 가 READY 만 조회).
data class WishPriceHistory(
    val wish: Wish,
    val item: Item,
    val history: List<ItemSnapshot>,
)
