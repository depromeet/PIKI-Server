package com.depromeet.piki.tournament.repository

import java.util.UUID

// itemId 로 역조회한 토너먼트 출전 좌표 — 파싱 알림 딥링크용. 누가(userId) 어느 토너먼트(tournamentId)에
// 어느 출전 아이템(tournamentItemId)으로 올렸는지. 수신자별 라우팅을 한 쿼리로 얻는다.
// Spring Data interface projection — JPQL 별칭(AS userId / AS tournamentId / AS tournamentItemId)이 getter 에 매핑된다.
interface TournamentItemRoutingView {
    val userId: UUID
    val tournamentId: Long
    val tournamentItemId: Long
}
