package com.depromeet.team3.wishlist.service.dto

// 위시리스트 cursor 페이지네이션 한 페이지의 결과.
// nextCursor 는 다음 요청에 그대로 돌려보낼 커서(마지막 항목 wishId 문자열), 마지막 페이지면 null.
data class WishlistPage(
    val entries: List<WishWithItem>,
    val nextCursor: String?,
    val hasNext: Boolean,
)
