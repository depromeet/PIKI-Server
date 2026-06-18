package com.depromeet.piki.notification.sse

import com.depromeet.piki.common.config.AsyncConfig
import com.depromeet.piki.item.domain.ItemStatus
import com.depromeet.piki.item.event.ItemParsingCompleted
import com.depromeet.piki.item.event.ItemParsingFailed
import com.depromeet.piki.notification.controller.dto.TournamentItemParsedPayload
import com.depromeet.piki.tournament.repository.TournamentItemRepository
import com.depromeet.piki.tournament.repository.TournamentUserRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

// 토너먼트 출전 아이템의 파싱 완료/실패를 그 토너먼트 참여자 화면에 실시간 반영한다.
//
// 문제: 주최자가 링크/이미지로 아이템을 추가하면 참여자는 TOURNAMENT_ITEM_ADDED 로 PENDING 카드(로딩바)를 띄우지만,
// 파싱이 끝나도 별도 신호가 없어 새로고침 전까지 로딩이 멈추지 않았다. 파싱 완료/실패 알림(ITEM_PARSING_*)은
// 올린 본인(adder)·위시 주인에게만 가고(노이즈 방지, ItemParsingRecipientResolver) 다른 참여자에겐 가지 않기 때문.
//
// 해결: 파싱이 끝나면(READY/FAILED) 그 아이템이 출전한 모든 토너먼트의 참여자 전원에게 SSE 라이브 동기화 이벤트
// (tournament-item-parsed)를 보내 카드를 갱신하게 한다. 이건 "알림"이 아니라 화면 갱신 신호라 알림센터·FCM 을
// 거치지 않고 SSE 로만 흐른다(NotificationDispatcher 경로와 별개 — 추가 알림 노이즈를 만들지 않는다).
//
// 결합 방향: 알림 -> 도메인 (단방향). item·tournament 도메인은 이 클래스를 모른다(자기 이벤트만 발행).
// AFTER_COMMIT + @Async: 파싱 상태 전이가 커밋된 뒤에만(롤백 시 미발송) 워커 스레드와 분리해 전달한다
// (notification 의 NotificationEventListener 와 같은 결, 같은 executor).
@Component
class TournamentItemParsedSseBroadcaster(
    private val tournamentItemRepository: TournamentItemRepository,
    private val tournamentUserRepository: TournamentUserRepository,
    private val localDelivery: LocalSseDelivery,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: ItemParsingCompleted) {
        broadcast(event.itemId, ItemStatus.READY)
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: ItemParsingFailed) {
        broadcast(event.itemId, ItemStatus.FAILED)
    }

    // itemId 로 그 아이템의 토너먼트 출전 좌표(tournamentItemId)와 참여자 전원을 풀어 카드 갱신을 보낸다.
    // adder(주최자)도 참여자라 함께 받는다 — 이 신호는 "알림"이 아니라 카드 갱신이라, 보는 화면이 모두 동일하게
    // 갱신되는 게 옳다(adder 의 ITEM_PARSING_COMPLETED 알림과는 목적이 다르다).
    //
    // 파싱 시점엔 add 마다 새 item·snapshot 이 생겨(persistLinkItem·persistProcessingItems) 한 itemId 가 한
    // tournament_item 에만 매이므로 routings 는 0~1 행이다. List·순회로 둔 건 향후 item 공유(한 아이템이 여러
    // 토너먼트에 동시 출전) 도입 대비이며, 그땐 routing 별로 각 snapshot 의 실제 상태를 확인해야 한다 — 지금은
    // 단일이라 이벤트 status 를 그대로 싣는다(공유 도입 시 spurious 갱신 주의, 후속 이슈). 위시 전용(어느 토너먼트에도
    // 없는) 아이템이면 routings 가 비어 아무 것도 보내지 않는다(위시 주인은 ITEM_PARSING_* 알림으로 받음).
    fun broadcast(
        itemId: Long,
        status: ItemStatus,
    ) {
        val routings = tournamentItemRepository.findRoutingByItemId(itemId)
        if (routings.isEmpty()) return
        routings.forEach { routing ->
            // 한 토너먼트의 조회·전달 실패가 나머지 fan-out 을 막지 않게 토너먼트 단위로 격리한다
            // (NotificationDispatcher 의 수신자 단위 격리와 같은 결). @Async 워커라 여기서 삼키지 않으면 기본
            // 핸들러가 맥락 없는 스택트레이스만 남겨 동기화 누락이 무음이 된다 — itemId 맥락을 실어 warn 으로 남긴다.
            runCatching {
                val participants = tournamentUserRepository.findByTournamentId(routing.tournamentId).map { it.userId }
                localDelivery.deliverTournamentItemParsed(
                    participants,
                    TournamentItemParsedPayload(
                        tournamentId = routing.tournamentId,
                        tournamentItemId = routing.tournamentItemId,
                        status = status,
                    ),
                )
                log.info(
                    "토너먼트 아이템 파싱 동기화 전송 tournamentId={} tournamentItemId={} status={} 참여자={}명",
                    routing.tournamentId,
                    routing.tournamentItemId,
                    status,
                    participants.size,
                )
            }.onFailure { e ->
                log.warn(
                    "토너먼트 아이템 파싱 동기화 실패 tournamentId={} itemId={} status={}",
                    routing.tournamentId,
                    itemId,
                    status,
                    e,
                )
            }
        }
    }
}
