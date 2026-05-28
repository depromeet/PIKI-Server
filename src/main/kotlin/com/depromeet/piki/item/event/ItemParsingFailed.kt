package com.depromeet.piki.item.event

// 아이템 파싱 실패 — 도메인 사실.
data class ItemParsingFailed(
    val itemId: Long,
)
