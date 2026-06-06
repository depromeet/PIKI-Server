package com.depromeet.piki.notification.controller.dto

import com.depromeet.piki.notification.domain.Notification
import com.depromeet.piki.notification.domain.NotificationKind
import com.depromeet.piki.notification.domain.NotificationType
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDateTime

// SSE 이벤트(name=notification)의 data 로 직렬화되는 payload.
// 스트림 자체는 text/event-stream 이라 ApiResponseBody 래퍼를 못 쓰지만, 각 이벤트 data 는 이 고정 JSON 으로 보낸다.
// 클라이언트는 type+refId 로 딥링크를 분기하고 title/body 를 표시한다. id 는 추후 읽음 처리(#246)의 키다.
@JsonInclude(JsonInclude.Include.NON_NULL)
data class NotificationSsePayload(
    val id: Long,
    val type: NotificationType,
    val title: String,
    val body: String,
    val refId: Long,
    val isRead: Boolean,
    val createdAt: LocalDateTime,
    // 파싱 알림 딥링크 라우팅(#408). 라우팅 컨텍스트가 없는 알림은 null 이라 NON_NULL 직렬화로 키가 빠진다.
    // FCM data 키와 같은 이름을 써, 클라가 SSE/FCM 어느 채널로 받든 같은 키로 읽는다(WISH 는 tournament_* 없음).
    val kind: NotificationKind? = null,
    val tournamentId: Long? = null,
    val tournamentItemId: Long? = null,
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
                kind = notification.kind,
                tournamentId = notification.tournamentId,
                tournamentItemId = notification.tournamentItemId,
            )
    }
}
