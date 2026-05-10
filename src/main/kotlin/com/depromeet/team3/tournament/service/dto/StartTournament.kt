package com.depromeet.team3.tournament.service.dto

data class CreateTournament(
    val name: String,
)

data class AddTournamentItems(
    val tournamentId: Long,
    val itemIds: List<Long>,
)
