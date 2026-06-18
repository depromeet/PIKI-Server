package com.depromeet.piki.notification.domain

// 타입별로 어떤 채널들로 보낼지 결정한다.
// 기준: 이 알림이 "순수 인앱 라이브 갱신(sync)" 인가 "남의 행동·내 작업 결과 알림(alert)" 인가.
//   - sync : 앱에서 SSE 로 즉시 반영하면 충분하고, OS 트레이 푸시는 노이즈다 → SSE 만.
//   - alert: 앱이 닫혀 있어도 알려야 하므로 OS 푸시까지 → SSE + PUSH 둘 다.
// 이 구분은 런타임 presence(온라인/포그라운드)가 아니라 타입 단위 정적 결정이라, 누락 갭·FE 의존 없이 안전하다.
// 온라인 유저가 alert 의 트레이 푸시를 중복으로 보지 않게 하는 건 클라의 포그라운드 표시 억제 몫이다(채널 선택과 무관).
//
// when 이 NotificationType 전수라 else 가 없다 — 새 타입을 추가하면 여기서 컴파일이 깨져 채널 분류를 강제한다(누락 방지).
object NotificationChannelPolicy {
    private val ALL = setOf(ChannelKind.SSE, ChannelKind.PUSH)
    private val SSE_ONLY = setOf(ChannelKind.SSE)

    fun kindsOf(type: NotificationType): Set<ChannelKind> =
        when (type) {
            // 출전 목록 라이브 갱신(sync) — 인앱에서 SSE 로 즉시 반영하면 충분하고 OS 트레이 푸시는 노이즈다.
            NotificationType.TOURNAMENT_ITEM_ADDED,
            NotificationType.TOURNAMENT_ITEM_DELETED,
            -> SSE_ONLY

            // 남의 행동·내 작업 결과·공지(alert) — 앱이 닫혀 있어도 알려야 하므로 OS 푸시까지 보낸다.
            NotificationType.TOURNAMENT_JOINED,
            NotificationType.TOURNAMENT_STARTED,
            NotificationType.TOURNAMENT_PLAYED_FROM_LINK,
            NotificationType.TOURNAMENT_COMPLETED,
            NotificationType.TOURNAMENT_RESULT_READY,
            NotificationType.ITEM_PARSING_COMPLETED,
            NotificationType.ITEM_PARSING_FAILED,
            NotificationType.ANNOUNCEMENT,
            -> ALL
        }
}
