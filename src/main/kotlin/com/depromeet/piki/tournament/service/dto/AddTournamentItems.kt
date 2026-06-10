package com.depromeet.piki.tournament.service.dto

data class AddTournamentItemsFromWish(
    val tournamentId: Long,
    val itemIds: List<Long>,
)
