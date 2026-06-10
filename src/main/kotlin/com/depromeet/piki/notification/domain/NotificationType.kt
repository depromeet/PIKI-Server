package com.depromeet.piki.notification.domain

// 알림 종류. 템플릿 조회 키이자 클라이언트의 딥링크 분기 키다.
// 새 알림 이벤트가 늘면 여기에 추가한다 (이벤트 data class + 핸들러 빈 + 템플릿 시드와 1:1 대응).
enum class NotificationType {
    TOURNAMENT_JOINED,
    TOURNAMENT_ITEM_ADDED,
    TOURNAMENT_STARTED,
    // 플레이링크로 내 토너먼트를 누군가 플레이/완료한 사실 — ROOT 주최자에게 간다(actor=플레이/완료한 사람).
    TOURNAMENT_PLAYED_FROM_LINK,
    TOURNAMENT_COMPLETED,
    // 내가 참여한 토너먼트를 주최자가 완료해 결과가 나온 사실 — 참여자에게 간다(actor=주최자).
    TOURNAMENT_RESULT_READY,
    ITEM_PARSING_COMPLETED,
    ITEM_PARSING_FAILED,
    // 전체 공지(#391/#250). 트리거(admin 백오피스)·발행은 후속 — 지금은 분류/필터용 enum 만 선반영한다.
    ANNOUNCEMENT,
}
