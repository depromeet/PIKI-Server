package com.depromeet.team3.tournament.controller.dto

import com.depromeet.team3.tournament.service.dto.TournamentHistoryInfo
import com.depromeet.team3.tournament.service.dto.TournamentInfo
import com.depromeet.team3.tournament.service.dto.TournamentItemInfo

data class TournamentInfoResponse(
    val tournamentId: Long,
    val round: Int,
    val items: List<TournamentItemInfoResponse>,
    val history: List<TournamentHistoryInfoResponse>,
) {
    companion object {
        fun from(tournamentInfo: TournamentInfo): TournamentInfoResponse = TournamentInfoResponse(
            tournamentId = tournamentInfo.tournamentId,
            round = tournamentInfo.items.size,
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
    val firstTournamentItemId: Long,
    val secondTournamentItemId: Long,
    val winnerTournamentItemId: Long,
) {
    companion object {
        fun from(history: TournamentHistoryInfo): TournamentHistoryInfoResponse = TournamentHistoryInfoResponse(
            currentRound = history.currentRound,
            firstTournamentItemId = history.firstTournamentItemId,
            secondTournamentItemId = history.secondTournamentItemId,
            winnerTournamentItemId = history.winnerTournamentItemId,
        )
    }
}
