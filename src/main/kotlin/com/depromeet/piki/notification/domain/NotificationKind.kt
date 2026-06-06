package com.depromeet.piki.notification.domain

// 파싱 알림(ITEM_PARSING_*)의 출처. 클라이언트 딥링크 분기 키 — WISH 면 /archive, TOURNAMENT 면 해당 토너먼트로 입장한다.
// 같은 type(ITEM_PARSING_COMPLETED 등)이 위시·토너먼트 양쪽에서 발행돼 refId 만으론 출처를 구분할 수 없으므로 별도로 내려보낸다.
enum class NotificationKind {
    WISH,
    TOURNAMENT,
}
