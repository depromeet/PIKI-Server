package com.depromeet.piki.tournament.service.dto

// 토너먼트 아이템 영속화 결과 — item PK 와 tournament_item PK 를 분리해 돌려준다.
// 비동기 파싱(parse)·상태 전이(markReady/markFailed)는 item PK 를 대상으로 하고,
// 클라이언트 응답·삭제(DELETE .../items/{tournamentItemId})에는 tournament_item PK 를 쓴다.
// 둘을 한 Long 으로 뭉뚱그리면 호출부가 혼동해 엉뚱한 item 을 전이시킨다.
data class PersistedTournamentItem(
    val itemId: Long,
    val tournamentItemId: Long,
)
