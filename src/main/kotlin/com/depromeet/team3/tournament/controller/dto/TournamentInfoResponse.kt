package com.depromeet.team3.tournament.controller.dto

import com.depromeet.team3.tournament.service.dto.TournamentHistoryInfo
import com.depromeet.team3.tournament.service.dto.TournamentInfo

data class TournamentInfoResponse(
    val tournamentId: Long,
    val round: Int,
    val finalWinnerWishItemId: Long?,
    val history: List<TournamentHistoryInfoResponse>,
) {
    companion object {
        fun from(tournamentInfo: TournamentInfo): TournamentInfoResponse =
            TournamentInfoResponse(
                tournamentId = tournamentInfo.tournamentId,
                round = tournamentInfo.round,
                finalWinnerWishItemId = tournamentInfo.finalWinnerWishItemId,
                history = tournamentInfo.history.map { TournamentHistoryInfoResponse.from(it) },
            )
    }
}

data class TournamentHistoryInfoResponse(
    val currentRound: Int,
    val firstWishItemId: Long,
    val secondWishItemId: Long,
    val winnerWishItemId: Long,
) {
    companion object {
        fun from(history: TournamentHistoryInfo): TournamentHistoryInfoResponse =
            TournamentHistoryInfoResponse(
                currentRound = history.currentRound,
                firstWishItemId = history.firstWishItemId,
                secondWishItemId = history.secondWishItemId,
                winnerWishItemId = history.winnerWishItemId,
            )
    }
}
