package com.depromeet.piki.notification.handler

import com.depromeet.piki.notification.domain.NotificationRouting
import com.depromeet.piki.notification.domain.Recipient
import com.depromeet.piki.tournament.repository.TournamentItemRepository
import com.depromeet.piki.wishlist.repository.WishRepository
import org.springframework.stereotype.Component
import java.util.UUID

// 아이템 파싱 완료·실패 알림의 수신자와 각자의 딥링크 라우팅을 itemId 로 역조회한다. 완료/실패 핸들러가 동일 규칙이라 공유한다.
//
// 라우팅은 "수신자별" 이다 — 같은 아이템이라도 위시 주인은 /archive(Wish), 토너먼트에 올린 본인(adder)은 자기 토너먼트로 가야 한다.
// (한 아이템이 위시·토너먼트에 공유될 수 있어 — 예: addItemsFromWish — 단일 라우팅을 전원에 찍으면 위시 주인이 남의 토너먼트로 샌다.)
// 충돌 규칙: 한 유저가 같은 아이템을 위시·토너먼트 양쪽에 둔 경우 위시(1차 등록 출처)를 우선한다.
// 한 유저가 같은 아이템을 여러 토너먼트에 올렸으면 id 가 가장 작은(가장 먼저 올린) 토너먼트로 본다.
// 토너먼트의 다른 참가자는 추가 시점에 TOURNAMENT_ITEM_ADDED 로 이미 알림을 받으므로 파싱완료를 또 보내지 않는다(노이즈 방지).
@Component
class ItemParsingRecipientResolver(
    private val wishRepository: WishRepository,
    private val tournamentItemRepository: TournamentItemRepository,
) {
    fun resolve(itemId: Long): Set<Recipient> {
        val routingByUser = mutableMapOf<UUID, NotificationRouting>()
        // 토너먼트 adder 별 자기 토너먼트 좌표. ORDER BY id ASC + putIfAbsent 라 가장 먼저 올린 토너먼트가 남는다.
        tournamentItemRepository.findRoutingByItemId(itemId).forEach {
            routingByUser.putIfAbsent(it.userId, NotificationRouting.Tournament(it.tournamentId, it.tournamentItemId))
        }
        // 위시 주인은 /archive. 위시·토너먼트 양쪽인 유저는 위시 우선이라 토너먼트 라우팅을 덮어쓴다.
        wishRepository.findUserIdsByItemId(itemId).forEach { routingByUser[it] = NotificationRouting.Wish }
        return routingByUser.map { Recipient(it.key, it.value) }.toSet()
    }
}
