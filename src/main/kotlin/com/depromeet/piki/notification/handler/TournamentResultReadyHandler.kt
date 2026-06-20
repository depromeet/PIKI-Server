package com.depromeet.piki.notification.handler

import com.depromeet.piki.notification.domain.NotificationType
import com.depromeet.piki.tournament.event.TournamentResultReady
import org.springframework.stereotype.Component
import java.util.UUID

// 주최자가 자기 토너먼트(ROOT)를 완료하면 참여자에게 "참여하신 OO님의 토너먼트 결과가 나왔어요" 를 보낸다.
// 수신자 = ROOT 참가자 ∪ 플레이링크 클론 소유자(게스트 포함) − 주최자 본인(actor). actor=주최자라 문구의 OO 는 주최자 이름·프사다.
@Component
class TournamentResultReadyHandler(
    private val recipientResolver: TournamentNotificationRecipientResolver,
    private val tournamentVariables: TournamentNotificationVariables,
) : NotificationEventHandler<TournamentResultReady>(NotificationType.TOURNAMENT_RESULT_READY) {
    override fun resolveRefId(event: TournamentResultReady): Long = event.rootTournamentId

    override fun resolveRecipients(event: TournamentResultReady): Set<UUID> =
        recipientResolver.resultParticipants(event.rootTournamentId) - event.actorId

    override fun resolveActorContext(event: TournamentResultReady): ActorContext =
        tournamentVariables.context(event.rootTournamentId, event.actorId)
}
