package com.depromeet.piki.tournament.repository

import com.depromeet.piki.tournament.domain.TournamentItem

interface TournamentItemRepository {
    fun saveAll(items: List<TournamentItem>): List<TournamentItem>

    fun countByTournamentId(tournamentId: Long): Int

    fun findIdsByTournamentId(tournamentId: Long): List<Long>

    // 이 아이템의 토너먼트 출전 좌표(adder userId + 어느 토너먼트 / 그 안 어느 tournament_item). 파싱 알림 수신자별 딥링크 라우팅 역조회(#408).
    fun findRoutingByItemId(itemId: Long): List<TournamentItemRoutingView>

    fun findAllByTournamentId(tournamentId: Long): List<TournamentItem>

    fun findAllByTournamentIds(ids: List<Long>): List<TournamentItem>

    fun findByIds(ids: List<Long>): List<TournamentItem>

    fun findById(id: Long): TournamentItem?

    fun softDeleteIfPending(
        id: Long,
        tournamentId: Long,
    ): Int

    fun softDeleteAllByTournamentId(tournamentId: Long)
}
