package com.depromeet.piki.notification.domain

// 알림센터 탭 구분(#473) — 활동(다른 사람의 행동) / 시스템(내 작업 결과·공지).
// 카테고리는 type 에서 파생한다(스키마 컬럼 없음) — 저장하지 않고 조회·직렬화 때 type 으로 계산한다.
// 주의: 카테고리(주제) ≠ 아바타(actor 유무). 예) 토너먼트 종료는 활동이지만 actor 가 없어 아바타는 피키 로고.
enum class NotificationCategory {
    ACTIVITY,
    SYSTEM,
    ;

    companion object {
        // type → category. when 이 NotificationType 전수라 else 가 없다 — 새 타입을 추가하면 여기서 컴파일이
        // 깨져 카테고리 분류를 강제한다(누락 방지).
        fun of(type: NotificationType): NotificationCategory =
            when (type) {
                NotificationType.TOURNAMENT_JOINED,
                NotificationType.TOURNAMENT_ITEM_ADDED,
                NotificationType.TOURNAMENT_STARTED,
                -> ACTIVITY

                NotificationType.ITEM_PARSING_COMPLETED,
                NotificationType.ITEM_PARSING_FAILED,
                -> SYSTEM
            }

        // 한 카테고리에 속한 type 들 — 목록 조회의 type-in 필터에 쓴다(?category=).
        fun typesOf(category: NotificationCategory): List<NotificationType> =
            NotificationType.entries.filter { of(it) == category }
    }
}
