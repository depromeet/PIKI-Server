package com.depromeet.team3.tournament.controller.dto

import com.depromeet.team3.tournament.service.dto.TournamentHistoryInfo
import com.depromeet.team3.tournament.service.dto.TournamentInfo
import com.depromeet.team3.tournament.service.dto.TournamentItemInfo

data class TournamentInfoResponse(
    val tournamentId: Long,
    val items: List<TournamentItemInfoResponse>,
    val history: List<TournamentHistoryInfoResponse>,
) {
    companion object {
        fun from(tournamentInfo: TournamentInfo): TournamentInfoResponse = TournamentInfoResponse(
            tournamentId = tournamentInfo.tournamentId,
            items = tournamentInfo.items.map { TournamentItemInfoResponse.from(it) },
            history = tournamentInfo.history.map { TournamentHistoryInfoResponse.from(it) },
        )
    }
}

data class TournamentItemInfoResponse(
    val tournamentItemId: Long,
    val itemId: Long,
) {
    companion object {
        fun from(item: TournamentItemInfo): TournamentItemInfoResponse = TournamentItemInfoResponse(
            tournamentItemId = item.tournamentItemId,
            itemId = item.itemId,
        )
    }
}

data class TournamentHistoryInfoResponse(
    val currentRound: Int,
    val firstItemId: Long,
    val secondItemId: Long,
    val winnerItemId: Long,
) {
    companion object {
        fun from(history: TournamentHistoryInfo): TournamentHistoryInfoResponse = TournamentHistoryInfoResponse(
            currentRound = history.currentRound,
            firstItemId = history.firstItemId,
            secondItemId = history.secondItemId,
            winnerItemId = history.winnerItemId,
        )
    }
}
