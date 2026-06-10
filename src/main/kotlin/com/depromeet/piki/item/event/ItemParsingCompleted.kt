package com.depromeet.piki.item.event

// 아이템 파싱 완료 — 도메인 사실(fact). 이 이벤트를 알림·통계·audit 등 어떤 소비자가 구독하든
// item 도메인은 그 존재를 모른다 (EDD 단방향 결합 — 소비자가 도메인을 import 한다).
data class ItemParsingCompleted(
    val itemId: Long,
)
