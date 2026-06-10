package com.depromeet.piki.notification.handler

import com.depromeet.piki.notification.domain.NotificationType
import com.depromeet.piki.tournament.event.TournamentItemAdded
import com.depromeet.piki.tournament.repository.TournamentUserRepository
import org.springframework.stereotype.Component
import java.util.UUID

// 토너먼트 아이템 추가 알림. 그 토너먼트 참가자들에게 보내되, 올린 본인(actor)은 제외한다 —
// 자기가 추가한 건 자기 화면이 이미 알고 있어 "남에게 알림" 성격이기 때문. 변수 actorName 으로 "OO님이 …" 를 채운다.
@Component
class TournamentItemAddedHandler(
    private val tournamentUserRepository: TournamentUserRepository,
    private val actorNameResolver: ActorNameResolver,
) : NotificationEventHandler<TournamentItemAdded>(NotificationType.TOURNAMENT_ITEM_ADDED) {
    override fun resolveRefId(event: TournamentItemAdded): Long = event.tournamentId

    override fun resolveRecipients(event: TournamentItemAdded): Set<UUID> =
        tournamentUserRepository.findByTournamentId(event.tournamentId).map { it.userId }.toSet() - event.actorId

    override fun resolveActorContext(event: TournamentItemAdded): ActorContext {
        val actor = actorNameResolver.resolveAttributes(event.actorId)
        return ActorContext(variables = mapOf("actorName" to actor.name), imageUrl = actor.profileImage)
    }
}
