package com.depromeet.piki.notification.service

import com.depromeet.piki.notification.domain.ChannelKind
import com.depromeet.piki.notification.domain.Notification
import java.util.UUID

// 알림 전달 채널. 새 채널 추가 = 이 인터페이스 구현 빈 1개 (SSE/FCM/…).
// Dispatcher 는 채널 목록을 순회만 하고, 전달 수단(로컬 write / Redis publish / FCM HTTP)은 구현이 숨긴다.
interface NotificationChannel {
    // 이 채널의 종류 — Dispatcher 가 타입별 채널 선택(NotificationChannelPolicy)으로 보낼 채널을 고를 때 식별자로 쓴다.
    val kind: ChannelKind

    fun send(
        userId: UUID,
        notification: Notification,
    )
}
