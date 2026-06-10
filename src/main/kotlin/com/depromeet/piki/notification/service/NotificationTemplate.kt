package com.depromeet.piki.notification.service

// 치환 전 템플릿. title/body 에 ${변수} 플레이스홀더를 담을 수 있다 (렌더러가 치환).
data class NotificationTemplate(
    val title: String,
    val body: String,
)
