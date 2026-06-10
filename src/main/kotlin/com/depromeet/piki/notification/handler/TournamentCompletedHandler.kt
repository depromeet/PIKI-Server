package com.depromeet.piki.notification.handler

import com.depromeet.piki.notification.domain.NotificationType
import com.depromeet.piki.tournament.event.TournamentCompleted
import org.springframework.stereotype.Component
import java.util.UUID

// 멤버/게스트가 자기 클론(=내 토너먼트의 클론)을 완료하면 ROOT 주최자에게 "OO님이 회원님 토너먼트를 완료했어요" 를 보낸다.
// 수신자 = 원본(ROOT) 주최자. actor(완료한 사람)가 곧 주최자면 제외(− actorId). (ROOT 자체 완료는 TournamentResultReady 가 담당)
@Component
class TournamentCompletedHandler(
    private val recipientResolver: TournamentNotificationRecipientResolver,
    private val actorNameResolver: ActorNameResolver,
) : NotificationEventHandler<TournamentCompleted>(NotificationType.TOURNAMENT_COMPLETED) {
    override fun resolveRefId(event: TournamentCompleted): Long = event.rootTournamentId

    override fun resolveRecipients(event: TournamentCompleted): Set<UUID> =
        recipientResolver.rootOwner(event.rootTournamentId) - event.actorId

    override fun resolveActorContext(event: TournamentCompleted): ActorContext {
        val actor = actorNameResolver.resolveAttributes(event.actorId)
        return ActorContext(variables = mapOf("actorName" to actor.name), imageUrl = actor.profileImage)
    }
}
