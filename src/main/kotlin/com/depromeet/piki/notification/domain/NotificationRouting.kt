package com.depromeet.piki.notification.domain

// 파싱 알림의 딥링크 라우팅 컨텍스트 — 서버는 완성 URL 을 박지 않고 도메인 식별자만 내려, 클라이언트가 URL 을 조립한다.
// kind 가 출처를 가르고, TOURNAMENT 는 "어느 토너먼트(tournamentId) / 그 안 어느 아이템(tournamentItemId)" 2좌표를 함께 싣는다
// (클라가 토너먼트로 입장한 뒤 그 아이템을 지목하려면 둘 다 필요하다). WISH 는 /archive 라 식별자가 없다 —
// "WISH 엔 식별자가 없고 TOURNAMENT 만 두 식별자를 가진다" 는 불변식을 타입으로 강제하려고 sealed 로 둔다.
sealed interface NotificationRouting {
    val kind: NotificationKind

    data object Wish : NotificationRouting {
        override val kind: NotificationKind = NotificationKind.WISH
    }

    data class Tournament(
        val tournamentId: Long,
        val tournamentItemId: Long,
    ) : NotificationRouting {
        override val kind: NotificationKind = NotificationKind.TOURNAMENT
    }
}
