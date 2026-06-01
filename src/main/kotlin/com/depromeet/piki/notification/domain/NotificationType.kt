package com.depromeet.piki.notification.domain

// 알림 종류. 템플릿 조회 키이자 클라이언트의 딥링크 분기 키다.
// 새 알림 이벤트가 늘면 여기에 추가한다 (이벤트 data class + 핸들러 빈 + 템플릿 시드와 1:1 대응).
enum class NotificationType {
    TOURNAMENT_JOINED,
    TOURNAMENT_ITEM_ADDED,
    ITEM_PARSING_COMPLETED,
    ITEM_PARSING_FAILED,
}
