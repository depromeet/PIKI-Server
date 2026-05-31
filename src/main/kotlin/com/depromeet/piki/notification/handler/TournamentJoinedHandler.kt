package com.depromeet.piki.notification.handler

import com.depromeet.piki.notification.domain.NotificationType
import com.depromeet.piki.tournament.event.TournamentJoined
import org.springframework.stereotype.Component
import java.util.UUID

// 토너먼트 참여 알림. tournamentId 역조회로 수신자를, actorId 로 변수(actorName)를 정한다.
// 발행 지점(참여 기능)은 아직 없지만, 이벤트와 짝을 맞춰 핸들러를 등록해 둔다 —
// 핸들러가 없으면 발행 시 Dispatcher 가 "핸들러 미등록" error 를 던지므로, 골격을 미리 둬 비대칭을 없앤다.
@Component
class TournamentJoinedHandler : NotificationEventHandler<TournamentJoined>(
    TournamentJoined::class,
    NotificationType.TOURNAMENT_JOINED,
) {
    override fun resolveRefId(event: TournamentJoined): Long = event.tournamentId

    // TODO(#236 수신자 정책 합의 + 참여 기능 신설): tournamentId 역조회로 수신자 결정 후 구현.
    override fun resolveRecipients(event: TournamentJoined): List<UUID> = emptyList()

    // TODO(#236): actorId -> actorName 닉네임 조회. 수신자 핸들러 완성 시 함께 채운다.
    override fun resolveVariables(event: TournamentJoined): Map<String, String> = emptyMap()
}
