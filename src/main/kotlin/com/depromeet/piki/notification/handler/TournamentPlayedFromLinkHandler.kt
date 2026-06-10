package com.depromeet.piki.notification.handler

import com.depromeet.piki.notification.domain.NotificationType
import com.depromeet.piki.tournament.event.TournamentPlayedFromLink
import org.springframework.stereotype.Component
import java.util.UUID

// 플레이링크로 누군가 내 토너먼트를 플레이하기 시작하면 ROOT 주최자에게 "OO님이 회원님 토너먼트를 플레이했어요" 를 보낸다.
// 수신자 = 원본(ROOT) 주최자. actor(플레이한 사람)가 곧 주최자면 제외(− actorId)해 자기 알림을 막는다.
@Component
class TournamentPlayedFromLinkHandler(
    private val recipientResolver: TournamentNotificationRecipientResolver,
    private val actorNameResolver: ActorNameResolver,
) : NotificationEventHandler<TournamentPlayedFromLink>(NotificationType.TOURNAMENT_PLAYED_FROM_LINK) {
    override fun resolveRefId(event: TournamentPlayedFromLink): Long = event.rootTournamentId

    override fun resolveRecipients(event: TournamentPlayedFromLink): Set<UUID> =
        recipientResolver.rootOwner(event.rootTournamentId) - event.actorId

    override fun resolveActorContext(event: TournamentPlayedFromLink): ActorContext {
        val actor = actorNameResolver.resolveAttributes(event.actorId)
        return ActorContext(variables = mapOf("actorName" to actor.name), imageUrl = actor.profileImage)
    }
}
