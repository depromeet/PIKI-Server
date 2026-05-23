package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.tournament.service.dto.AddTournamentItems
import jakarta.validation.constraints.Size

data class AddTournamentItemsRequest(
    @field:Size(min = 1, max = 32)
    val itemIds: List<Long>,
) {
    fun toAddTournamentItems(tournamentId: Long): AddTournamentItems =
        AddTournamentItems(
            tournamentId = tournamentId,
            itemIds = itemIds,
        )
}
