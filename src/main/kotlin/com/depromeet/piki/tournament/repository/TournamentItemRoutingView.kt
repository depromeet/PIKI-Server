package com.depromeet.piki.tournament.repository

// itemId 로 역조회한 토너먼트 출전 좌표 — 파싱 알림 딥링크용(어느 토너먼트 / 그 안 어느 tournament_item).
// Spring Data interface projection — JPQL 의 별칭(AS tournamentId / AS tournamentItemId)이 getter 에 매핑된다.
interface TournamentItemRoutingView {
    val tournamentId: Long
    val tournamentItemId: Long
}
