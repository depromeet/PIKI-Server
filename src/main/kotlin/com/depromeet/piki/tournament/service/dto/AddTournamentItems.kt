package com.depromeet.piki.tournament.service.dto

data class AddTournamentItems(
    val tournamentId: Long,
    val itemIds: List<Long>,
)
