package com.depromeet.piki.notification.handler

import com.depromeet.piki.notification.domain.NotificationType
import com.depromeet.piki.tournament.event.TournamentJoined
import com.depromeet.piki.tournament.repository.TournamentUserRepository
import org.springframework.stereotype.Component
import java.util.UUID

// 토너먼트 참여 알림. 기존 참가자들에게 "OO님이 참가했어요" 를 보내되, 새로 들어온 본인(actor)은 제외한다.
// 이벤트가 AFTER_COMMIT 라 actor 는 이미 참가자 목록에 들어있으므로 빼줘야 한다.
//
// 발행 지점(참여 기능)은 아직 없지만, 이벤트와 짝을 맞춰 핸들러를 등록해 둔다 — 발행되는 순간 바로 동작한다.
@Component
class TournamentJoinedHandler(
    private val tournamentUserRepository: TournamentUserRepository,
    private val actorNameResolver: ActorNameResolver,
) : NotificationEventHandler<TournamentJoined>(NotificationType.TOURNAMENT_JOINED) {
    override fun resolveRefId(event: TournamentJoined): Long = event.tournamentId

    override fun resolveRecipients(event: TournamentJoined): Set<UUID> =
        tournamentUserRepository.findByTournamentId(event.tournamentId).map { it.userId }.toSet() - event.actorId

    override fun resolveVariables(event: TournamentJoined): Map<String, String> =
        mapOf("actorName" to actorNameResolver.resolve(event.actorId))

    override fun resolveActorImageUrl(event: TournamentJoined): String? = actorNameResolver.resolveProfileImage(event.actorId)
}
