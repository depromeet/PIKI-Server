package com.depromeet.team3.tournament.service.dto

data class RecordMatch(
    val tournamentId: Long,
    val currentRound: Int,
    val firstItemId: Long,
    val secondItemId: Long,
    val winnerItemId: Long,
)

