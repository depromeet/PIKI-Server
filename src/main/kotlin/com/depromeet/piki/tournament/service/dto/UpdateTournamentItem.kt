package com.depromeet.piki.tournament.service.dto

data class UpdateTournamentItem(
    val tournamentId: Long,
    val tournamentItemId: Long,
    val name: String?,
    val price: Int?,
    val currency: String?,
    val imageUrl: String?,
)
