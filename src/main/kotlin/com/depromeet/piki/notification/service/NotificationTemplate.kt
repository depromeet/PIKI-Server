package com.depromeet.piki.notification.service

// 치환 전 템플릿. title/body 에 ${변수} 플레이스홀더를 담을 수 있다 (렌더러가 치환).
data class NotificationTemplate(
    val title: String,
    val body: String,
    // 이 타입을 OS 푸시(FCM)까지 보낼지. SSE·알림센터는 타입 무관 항상 전달되고, 타입별로 갈리는 건 이 한 축뿐이다.
    // 백오피스에서 토글하면 PushNotificationChannel 이 발송 직전 이 값으로 자기-적용 판단한다(옛 NotificationChannelPolicy.pushable 대체).
    val pushEnabled: Boolean,
)
