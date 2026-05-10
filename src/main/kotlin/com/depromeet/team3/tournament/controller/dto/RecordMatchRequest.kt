package com.depromeet.team3.tournament.controller.dto

import com.depromeet.team3.tournament.service.dto.RecordMatch

data class RecordMatchRequest(
    val currentRound: Int,
    val firstItemId: Long,
    val secondItemId: Long,
    val winnerItemId: Long,
) {
    fun toRecordMatch(tournamentId: Long): RecordMatch = RecordMatch(
        tournamentId = tournamentId,
        currentRound = currentRound,
        firstItemId = firstItemId,
        secondItemId = secondItemId,
        winnerItemId = winnerItemId,
    )
}

