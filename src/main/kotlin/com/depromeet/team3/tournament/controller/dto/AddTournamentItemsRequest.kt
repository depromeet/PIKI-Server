package com.depromeet.team3.tournament.controller.dto

import com.depromeet.team3.tournament.service.dto.AddTournamentItems
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class AddTournamentItemsRequest(
    @field:NotEmpty
    @field:Size(min = 2)
    val itemIds: List<Long>,
) {
    fun toAddTournamentItems(tournamentId: Long): AddTournamentItems = AddTournamentItems(
        tournamentId = tournamentId,
        itemIds = itemIds,
    )
}
