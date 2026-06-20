package com.depromeet.piki.notification.handler

import com.depromeet.piki.item.repository.ItemSnapshotRepository
import com.depromeet.piki.notification.domain.NotificationRouting
import com.depromeet.piki.notification.domain.NotificationType
import com.depromeet.piki.tournament.event.TournamentItemDeleted
import com.depromeet.piki.tournament.repository.TournamentUserRepository
import org.springframework.stereotype.Component
import java.util.UUID

// 토너먼트 아이템 삭제 알림. 그 토너먼트 참가자들에게 보내되, 삭제한 본인(actor=등록자 또는 주최자)은 제외한다 —
// 자기가 지운 건 자기 화면이 이미 알고 있어 "남에게 알림" 성격이기 때문(TOURNAMENT_ITEM_ADDED 와 동일 규칙).
// 추가와 달리 삭제는 클라가 그 아이템을 이미 들고 있어, 어느 아이템인지(tournamentItemId)와 상품명(itemName)을 실어
// 재조회 없이 그 항목만 빼고 "OO님이 '상품명'을 삭제" 로 보여줄 수 있게 한다.
//   - refId=tournamentId : 딥링크(탭하면 그 토너먼트로 입장). 추가 알림과 동일.
//   - routing=Tournament(tournamentId, tournamentItemId) : payload 에 아이템 좌표를 싣는다(파싱 알림과 공유 셰입).
//   - itemName : snapshotId 로 상품명 해석. 아직 파싱 전(PROCESSING)이라 name 이 없으면 fallback.
@Component
class TournamentItemDeletedHandler(
    private val tournamentUserRepository: TournamentUserRepository,
    private val tournamentVariables: TournamentNotificationVariables,
    private val itemSnapshotRepository: ItemSnapshotRepository,
) : NotificationEventHandler<TournamentItemDeleted>(NotificationType.TOURNAMENT_ITEM_DELETED) {
    override fun resolveRefId(event: TournamentItemDeleted): Long = event.tournamentId

    override fun resolveRecipients(event: TournamentItemDeleted): Set<UUID> =
        tournamentUserRepository.findByTournamentId(event.tournamentId).map { it.userId }.toSet() - event.actorId

    override fun resolveRouting(event: TournamentItemDeleted): NotificationRouting =
        NotificationRouting.Tournament(tournamentId = event.tournamentId, tournamentItemId = event.tournamentItemId)

    override fun resolveActorContext(event: TournamentItemDeleted): ActorContext {
        val base = tournamentVariables.context(event.tournamentId, event.actorId)
        val itemName = itemSnapshotRepository.findById(event.snapshotId)?.name ?: FALLBACK_ITEM_NAME
        return base.copy(variables = base.variables + ("itemName" to itemName))
    }

    companion object {
        // 삭제 시점에 상품명이 아직 없을 때(파싱 전 PENDING/PROCESSING 아이템 삭제)의 대체 문구.
        private const val FALLBACK_ITEM_NAME = "상품"
    }
}
