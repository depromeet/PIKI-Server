package com.depromeet.piki.notification.handler

import com.depromeet.piki.tournament.repository.TournamentItemRepository
import com.depromeet.piki.wishlist.repository.WishRepository
import org.springframework.stereotype.Component
import java.util.UUID

// 아이템 파싱 완료·실패 알림의 수신자를 itemId 로 역조회한다. 완료/실패 핸들러가 동일 규칙이라 공유한다.
//
// "파싱 완료/실패 알림" 은 그 아이템을 올려서 결과를 기다리던 본인에게 간다 = 위시 주인 ∪ 토너먼트에 올린 본인(adder).
// 토너먼트의 다른 참가자는 추가 시점에 TOURNAMENT_ITEM_ADDED("OO 이 추가함")로 이미 알림을 받아 화면을 갱신하므로,
// 파싱완료를 또 보내지 않는다(노이즈 방지). 한 아이템이 위시·여러 토너먼트에 공유되면 각 등록자(adder)가 각자 받고,
// Set 이라 같은 유저는 1번만 받는다.
@Component
class ItemParsingRecipientResolver(
    private val wishRepository: WishRepository,
    private val tournamentItemRepository: TournamentItemRepository,
) {
    fun resolve(itemId: Long): Set<UUID> {
        val wishOwners = wishRepository.findUserIdsByItemId(itemId)
        val tournamentAdders = tournamentItemRepository.findUserIdsByItemId(itemId)
        return (wishOwners + tournamentAdders).toSet()
    }
}
