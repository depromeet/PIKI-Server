package com.depromeet.team3.tournament.service.dto

import com.depromeet.team3.tournament.domain.Tournament
import com.depromeet.team3.tournament.domain.TournamentHistory

data class TournamentInfo(
    val tournamentId: Long,
    val round: Int,
    val finalWinnerWishItemId: Long?,
    val history: List<TournamentHistoryInfo>,
) {
    companion object {
        fun of(tournament: Tournament, histories: List<TournamentHistory>): TournamentInfo = TournamentInfo(
            tournamentId = tournament.getId(),
            round = tournament.round,
            finalWinnerWishItemId = tournament.finalWinnerWishItemId,
            history = histories.map(TournamentHistoryInfo::from),
        )
    }
}

data class TournamentHistoryInfo(
    val currentRound: Int,
    val firstWishItemId: Long,
    val secondWishItemId: Long,
    val winnerWishItemId: Long,
) {
    companion object {
        fun from(history: TournamentHistory): TournamentHistoryInfo = TournamentHistoryInfo(
            currentRound = history.currentRound,
            firstWishItemId = history.firstWishItemId,
            secondWishItemId = history.secondWishItemId,
            winnerWishItemId = history.winnerWishItemId,
        )
    }
}
