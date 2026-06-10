package com.depromeet.piki.notification.controller.dto

import com.depromeet.piki.notification.domain.Notification
import com.depromeet.piki.notification.domain.NotificationCategory
import com.depromeet.piki.notification.domain.NotificationKind
import com.depromeet.piki.notification.domain.NotificationRouting
import com.depromeet.piki.notification.domain.NotificationType
import java.time.LocalDateTime

// SSE 이벤트(name=notification)의 data 로 직렬화되는 payload.
// 알림 종류별로 셰입이 다르다 — 라우팅 컨텍스트(#408)가 없는 알림은 refId 만, 파싱 알림은 출처(kind)별로 식별자가 갈린다.
// nullable 잡탕 + NON_NULL 로 런타임에 가리는 대신, sealed 로 각 셰입을 타입에 고정한다(도메인 NotificationRouting 과 같은 결).
// 클라이언트는 type 으로 화면을, 파싱 알림은 kind 로 출처를 분기한다. id 는 추후 읽음 처리(#246)의 키다.
//
// imageUrl·category(#473): imageUrl 은 항상 채워 내려간다 — 사람 알림은 발송 시점 actor 프사 snapshot,
// 시스템 알림은 서버가 defaultPushImg(피키 로고)로 채운다. 클라는 null-check 없이 imageUrl 을 그대로 아바타로 렌더하고,
// 사람/시스템 구분(탭·시각 차이)은 category(ACTIVITY/SYSTEM)로 한다. category 는 type 에서 파생한다(스키마 컬럼 없음).
sealed interface NotificationSsePayload {
    val id: Long
    val type: NotificationType
    val category: NotificationCategory
    val title: String
    val body: String
    val imageUrl: String
    val refId: Long
    val isRead: Boolean
    val createdAt: LocalDateTime

    // 라우팅 컨텍스트가 없는 알림(토너먼트 알림 등). refId 만으로 딥링크가 결정된다(예: refId=tournamentId).
    data class Reference(
        override val id: Long,
        override val type: NotificationType,
        override val category: NotificationCategory,
        override val title: String,
        override val body: String,
        override val imageUrl: String,
        override val refId: Long,
        override val isRead: Boolean,
        override val createdAt: LocalDateTime,
    ) : NotificationSsePayload

    // 위시 출처 파싱 알림. refId(=itemId) + kind=WISH. 토너먼트 식별자는 셰입에 아예 없다(클라는 /archive 로).
    data class WishParsing(
        override val id: Long,
        override val type: NotificationType,
        override val category: NotificationCategory,
        override val title: String,
        override val body: String,
        override val imageUrl: String,
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
        override val category: NotificationCategory,
        override val title: String,
        override val body: String,
        override val imageUrl: String,
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
        // imageUrl = actor 스냅샷(actorImageUrl) 이 있으면 그것, 없으면(시스템) defaultPushImageUrl 로 채운다 — 항상 비지 않는다.
        // category 는 type 에서 파생한다(NotificationCategory.of). defaultPushImageUrl 은 호출자(채널·응답)가 DefaultPushImage 에서 넘긴다.
        fun from(
            notification: Notification,
            defaultPushImageUrl: String,
        ): NotificationSsePayload {
            val id = notification.getId()
            val category = NotificationCategory.of(notification.type)
            val imageUrl = notification.actorImageUrl ?: defaultPushImageUrl
            return when (val routing = notification.routing()) {
                null ->
                    Reference(
                        id = id,
                        type = notification.type,
                        category = category,
                        title = notification.title,
                        body = notification.body,
                        imageUrl = imageUrl,
                        refId = notification.refId,
                        isRead = notification.isRead,
                        createdAt = notification.createdAt,
                    )

                NotificationRouting.Wish ->
                    WishParsing(
                        id = id,
                        type = notification.type,
                        category = category,
                        title = notification.title,
                        body = notification.body,
                        imageUrl = imageUrl,
                        refId = notification.refId,
                        isRead = notification.isRead,
                        createdAt = notification.createdAt,
                    )

                is NotificationRouting.Tournament ->
                    TournamentParsing(
                        id = id,
                        type = notification.type,
                        category = category,
                        title = notification.title,
                        body = notification.body,
                        imageUrl = imageUrl,
                        refId = notification.refId,
                        isRead = notification.isRead,
                        createdAt = notification.createdAt,
                        tournamentId = routing.tournamentId,
                        tournamentItemId = routing.tournamentItemId,
                    )
            }
        }
    }
}
