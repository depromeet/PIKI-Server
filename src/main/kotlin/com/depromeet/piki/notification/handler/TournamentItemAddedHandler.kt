package com.depromeet.piki.notification.handler

import com.depromeet.piki.notification.domain.NotificationType
import com.depromeet.piki.tournament.event.TournamentItemAdded
import org.springframework.stereotype.Component
import java.util.UUID

// 토너먼트 아이템 추가 알림. tournamentId 역조회로 수신자를, actorId 로 변수(actorName)를 정한다.
@Component
class TournamentItemAddedHandler : NotificationEventHandler<TournamentItemAdded> {
    override val eventType = TournamentItemAdded::class
    override val notificationType = NotificationType.TOURNAMENT_ITEM_ADDED

    override fun resolveRefId(event: TournamentItemAdded): Long = event.tournamentId

    // TODO(#236 수신자 정책 합의): tournamentId 역조회 — owner-only vs 참가자 fan-out(actor 제외) 결정 후 구현.
    override fun resolveRecipients(event: TournamentItemAdded): List<UUID> = emptyList()

    // TODO(#236): actorId -> actorName 닉네임 조회. 수신자 핸들러 완성 시 함께 채운다.
    override fun resolveVariables(event: TournamentItemAdded): Map<String, String> = emptyMap()
}
