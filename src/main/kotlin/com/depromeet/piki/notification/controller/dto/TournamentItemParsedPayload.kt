package com.depromeet.piki.notification.controller.dto

import com.depromeet.piki.item.domain.ItemStatus

// 토너먼트 출전 아이템의 파싱이 끝났음을 토너먼트 참여자 화면에 실시간 반영하는 SSE 라이브 동기화 payload
// (event name = "tournament-item-parsed"). 이건 "알림"(notification)이 아니라 화면 갱신 신호다 —
// 알림센터·FCM 푸시를 거치지 않고 SSE 로만 흐른다(NotificationSsePayload 와 별개 경로).
//
// 클라는 (tournamentId, tournamentItemId)로 그 토너먼트의 해당 출전 카드를 찾아 status 로 갱신한다:
// PENDING/PROCESSING 로딩 → READY(상품 정보 표시) / FAILED(에러·재시도). status 는 항상 READY·FAILED 중 하나다.
data class TournamentItemParsedPayload(
    val tournamentId: Long,
    val tournamentItemId: Long,
    val status: ItemStatus,
)
