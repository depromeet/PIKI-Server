package com.depromeet.piki.notification.handler

import com.depromeet.piki.notification.domain.NotificationType
import com.depromeet.piki.tournament.event.TournamentStarted
import com.depromeet.piki.tournament.repository.TournamentUserRepository
import org.springframework.stereotype.Component
import java.util.UUID

// 토너먼트 시작 알림. 주최자가 시작하면 나머지 참가자에게 "OO님이 토너먼트를 시작했어요" 를 보내 함께 진입하게 한다.
// 시작시킨 본인(actor=주최자)은 자기 화면이 이미 시작 상태로 넘어가므로 제외한다 — joined/itemAdded 와 같은 actor 제외 패턴.
@Component
class TournamentStartedHandler(
    private val tournamentUserRepository: TournamentUserRepository,
    private val tournamentVariables: TournamentNotificationVariables,
) : NotificationEventHandler<TournamentStarted>(NotificationType.TOURNAMENT_STARTED) {
    override fun resolveRefId(event: TournamentStarted): Long = event.tournamentId

    override fun resolveRecipients(event: TournamentStarted): Set<UUID> =
        tournamentUserRepository.findByTournamentId(event.tournamentId).map { it.userId }.toSet() - event.actorId

    override fun resolveActorContext(event: TournamentStarted): ActorContext =
        tournamentVariables.context(event.tournamentId, event.actorId)
}
