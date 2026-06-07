package com.depromeet.piki.notification.domain

import java.util.UUID

// 알림 한 건의 수신자 — 누구에게(userId) 어디로(routing) 가는지. 라우팅은 수신자에 묶인다:
// 같은 파싱 아이템이라도 위시 주인은 /archive(Wish), 토너먼트에 올린 본인은 자기 토너먼트(Tournament)로 가야 하므로,
// "아이템당 1개" 가 아니라 "수신자당 1개" 다. 라우팅이 필요 없는 알림(토너먼트 알림 등)은 null.
data class Recipient(
    val userId: UUID,
    val routing: NotificationRouting? = null,
)
