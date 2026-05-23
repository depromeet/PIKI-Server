package com.depromeet.piki.tournament.service.dto

data class RecordMatch(
    val tournamentId: Long,
    val currentRound: Int,
    val firstTournamentItemId: Long,
    val secondTournamentItemId: Long,
    val selectedTournamentItemId: Long,
)
