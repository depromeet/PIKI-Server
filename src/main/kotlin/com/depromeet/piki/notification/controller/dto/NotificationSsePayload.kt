package com.depromeet.piki.notification.controller.dto

import com.depromeet.piki.notification.domain.Notification
import com.depromeet.piki.notification.domain.NotificationKind
import com.depromeet.piki.notification.domain.NotificationRouting
import com.depromeet.piki.notification.domain.NotificationType
import java.time.LocalDateTime

// SSE 이벤트(name=notification)의 data 로 직렬화되는 payload.
// 알림 종류별로 셰입이 다르다 — 라우팅 컨텍스트(#408)가 없는 알림은 refId 만, 파싱 알림은 출처(kind)별로 식별자가 갈린다.
// nullable 잡탕 + NON_NULL 로 런타임에 가리는 대신, sealed 로 각 셰입을 타입에 고정한다(도메인 NotificationRouting 과 같은 결).
// 클라이언트는 type 으로 화면을, 파싱 알림은 kind 로 출처를 분기한다. id 는 추후 읽음 처리(#246)의 키다.
sealed interface NotificationSsePayload {
    val id: Long
    val type: NotificationType
    val title: String
    val body: String
    val refId: Long
    val isRead: Boolean
    val createdAt: LocalDateTime

    // 라우팅 컨텍스트가 없는 알림(토너먼트 알림 등). refId 만으로 딥링크가 결정된다(예: refId=tournamentId).
    data class Reference(
        override val id: Long,
        override val type: NotificationType,
        override val title: String,
        override val body: String,
        override val refId: Long,
        override val isRead: Boolean,
        override val createdAt: LocalDateTime,
    ) : NotificationSsePayload

    // 위시 출처 파싱 알림. refId(=itemId) + kind=WISH. 토너먼트 식별자는 셰입에 아예 없다(클라는 /archive 로).
    data class WishParsing(
        override val id: Long,
        override val type: NotificationType,
        override val title: String,
        override val body: String,
        override val refId: Long,
        override val isRead: Boolean,
        override val createdAt: LocalDateTime,
    ) : NotificationSsePayload {
        val kind: NotificationKind = NotificationKind.WISH
    }

    // 토너먼트 출처 파싱 알림. refId(=itemId) + kind=TOURNAMENT + 입장(tournamentId)·아이템 지목(tournamentItemId).
    data class TournamentParsing(
        override val id: Long,
        override val type: NotificationType,
        override val title: String,
        override val body: String,
        override val refId: Long,
        override val isRead: Boolean,
        override val createdAt: LocalDateTime,
        val tournamentId: Long,
        val tournamentItemId: Long,
    ) : NotificationSsePayload {
        val kind: NotificationKind = NotificationKind.TOURNAMENT
    }

    companion object {
        // 채널에 도달한 Notification 은 dispatcher 가 이미 저장한 영속 엔티티라 id 가 보장된다(getId()).
        // 라우팅 컨텍스트(routing())로 셰입을 가른다 — 없으면 Reference, 위시/토너먼트면 각 파싱 payload.
        fun from(notification: Notification): NotificationSsePayload {
            val id = notification.getId()
            return when (val routing = notification.routing()) {
                null ->
                    Reference(
                        id, notification.type, notification.title, notification.body,
                        notification.refId, notification.isRead, notification.createdAt,
                    )

                NotificationRouting.Wish ->
                    WishParsing(
                        id, notification.type, notification.title, notification.body,
                        notification.refId, notification.isRead, notification.createdAt,
                    )

                is NotificationRouting.Tournament ->
                    TournamentParsing(
                        id, notification.type, notification.title, notification.body,
                        notification.refId, notification.isRead, notification.createdAt,
                        routing.tournamentId, routing.tournamentItemId,
                    )
            }
        }
    }
}
