package com.depromeet.piki.notification.domain

// 알림 타입이 OS 푸시(FCM)까지 가야 하는지 결정한다.
// 모든 알림은 인앱 채널(SSE)로 받으므로 타입별로 갈리는 건 "푸시까지 보내나" 하나뿐 — 그 한 가지만 여기서 정한다.
//   - sync (인앱 라이브 갱신: 아이템 추가/삭제) -> false. SSE 로 즉시 반영하면 충분하고 OS 트레이 푸시는 노이즈다.
//   - alert (남의 행동·내 작업 결과·공지) -> true. 앱이 닫혀 있어도 알려야 한다.
// 이 구분은 런타임 presence(온라인/포그라운드)가 아니라 타입 단위 정적 결정이다. 온라인 유저가 alert 트레이 푸시를
// 중복으로 보지 않게 하는 건 클라의 포그라운드 표시 억제 몫(이 정책과 무관).
//
// when 이 NotificationType 전수라 else 가 없다 — 새 타입을 추가하면 여기서 컴파일이 깨져 분류를 강제한다(누락 방지).
object NotificationChannelPolicy {
    fun pushable(type: NotificationType): Boolean =
        when (type) {
            NotificationType.TOURNAMENT_ITEM_ADDED,
            NotificationType.TOURNAMENT_ITEM_DELETED,
            -> false

            NotificationType.TOURNAMENT_JOINED,
            NotificationType.TOURNAMENT_STARTED,
            NotificationType.TOURNAMENT_PLAYED_FROM_LINK,
            NotificationType.TOURNAMENT_COMPLETED,
            NotificationType.TOURNAMENT_RESULT_READY,
            NotificationType.ITEM_PARSING_COMPLETED,
            NotificationType.ITEM_PARSING_FAILED,
            NotificationType.ANNOUNCEMENT,
            -> true
        }
}
