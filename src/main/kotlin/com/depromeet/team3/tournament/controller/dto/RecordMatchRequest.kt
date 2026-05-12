package com.depromeet.team3.tournament.controller.dto

import com.depromeet.team3.tournament.service.dto.RecordMatch

data class RecordMatchRequest(
    val currentRound: Int,
    val firstWishItemId: Long,
    val secondWishItemId: Long,
    val winnerWishItemId: Long,
) {
    fun toRecordMatch(tournamentId: Long): RecordMatch =
        RecordMatch(
            tournamentId = tournamentId,
            currentRound = currentRound,
            firstWishItemId = firstWishItemId,
            secondWishItemId = secondWishItemId,
            winnerWishItemId = winnerWishItemId,
        )
}
