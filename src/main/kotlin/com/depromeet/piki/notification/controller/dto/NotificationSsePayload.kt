package com.depromeet.piki.notification.controller.dto

import com.depromeet.piki.notification.domain.Notification
import com.depromeet.piki.notification.domain.NotificationType
import java.time.LocalDateTime

// SSE 이벤트(name=notification)의 data 로 직렬화되는 payload.
// 스트림 자체는 text/event-stream 이라 ApiResponseBody 래퍼를 못 쓰지만, 각 이벤트 data 는 이 고정 JSON 으로 보낸다.
// 클라이언트는 type+refId 로 딥링크를 분기하고 title/body 를 표시한다. id 는 추후 읽음 처리(#246)의 키다.
data class NotificationSsePayload(
    val id: Long,
    val type: NotificationType,
    val title: String,
    val body: String,
    val refId: Long,
    val isRead: Boolean,
    val createdAt: LocalDateTime,
) {
    companion object {
        // 채널에 도달한 Notification 은 dispatcher 가 이미 저장한 영속 엔티티라 id 가 보장된다(getId()).
        fun from(notification: Notification): NotificationSsePayload =
            NotificationSsePayload(
                id = notification.getId(),
                type = notification.type,
                title = notification.title,
                body = notification.body,
                refId = notification.refId,
                isRead = notification.isRead,
                createdAt = notification.createdAt,
            )
    }
}
