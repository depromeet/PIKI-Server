package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.tournament.service.dto.RecordMatch

data class RecordMatchRequest(
    val currentRound: Int,
    val firstTournamentItemId: Long,
    val secondTournamentItemId: Long,
    val selectedTournamentItemId: Long,
) {
    fun toRecordMatch(tournamentId: Long): RecordMatch =
        RecordMatch(
            tournamentId = tournamentId,
            currentRound = currentRound,
            firstTournamentItemId = firstTournamentItemId,
            secondTournamentItemId = secondTournamentItemId,
            selectedTournamentItemId = selectedTournamentItemId,
        )
}
