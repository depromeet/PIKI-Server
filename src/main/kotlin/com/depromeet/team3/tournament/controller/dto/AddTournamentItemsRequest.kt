package com.depromeet.team3.tournament.controller.dto

import com.depromeet.team3.tournament.service.dto.AddTournamentItem

data class AddTournamentItemRequest(
    val itemId: Long,
) {
    fun toAddTournamentItem(tournamentId: Long): AddTournamentItem =
        AddTournamentItem(
            tournamentId = tournamentId,
            itemId = itemId,
        )
}
