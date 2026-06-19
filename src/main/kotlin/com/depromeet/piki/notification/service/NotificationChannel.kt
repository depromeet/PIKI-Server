package com.depromeet.piki.notification.service

import com.depromeet.piki.notification.domain.Notification
import java.util.UUID

// 알림 전달 채널. 새 채널 추가 = 이 인터페이스 구현 빈 1개 (SSE/FCM/…).
// Dispatcher 는 채널 목록을 순회만 하고, 전달 수단(로컬 write / Redis publish / FCM HTTP)은 구현이 숨긴다.
// 어떤 타입을 다룰지(예: FCM 은 push 대상 타입만)는 각 구현이 send 안에서 자기-적용 판단한다.
interface NotificationChannel {
    fun send(
        userId: UUID,
        notification: Notification,
    )
}
