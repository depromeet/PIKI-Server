package com.depromeet.team3.tournament.service.dto

import com.depromeet.team3.tournament.domain.Tournament
import com.depromeet.team3.tournament.domain.TournamentHistory
import com.depromeet.team3.tournament.domain.TournamentItem

data class TournamentInfo(
    val tournamentId: Long,
    val items: List<TournamentItemInfo>,
    val history: List<TournamentHistoryInfo>,
) {
    companion object {
        fun of(
            tournament: Tournament,
            items: List<TournamentItem>,
            histories: List<TournamentHistory>,
        ): TournamentInfo = TournamentInfo(
            tournamentId = tournament.getId(),
            items = items.map { TournamentItemInfo(tournamentItemId = it.getId(), itemId = it.itemId) },
            history = histories.map(TournamentHistoryInfo::from),
        )
    }
}

data class TournamentItemInfo(
    val tournamentItemId: Long,
    val itemId: Long,
)

data class TournamentHistoryInfo(
    val currentRound: Int,
    val firstTournamentItemId: Long,
    val secondTournamentItemId: Long,
    val selectedTournamentItemId: Long,
) {
    companion object {
        fun from(history: TournamentHistory): TournamentHistoryInfo = TournamentHistoryInfo(
            currentRound = history.currentRound,
            firstTournamentItemId = history.firstTournamentItemId,
            secondTournamentItemId = history.secondTournamentItemId,
            selectedTournamentItemId = history.selectedTournamentItemId,
        )
    }
}
