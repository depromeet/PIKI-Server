package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.tournament.service.dto.AddTournamentItemsFromWish
import jakarta.validation.constraints.Size

data class AddTournamentItemsRequest(
    @field:Size(min = 1, max = 32, message = "아이템은 1개 이상 32개 이하여야 합니다.")
    val itemIds: List<Long>,
) {
    fun toAddTournamentItemsFromWish(tournamentId: Long): AddTournamentItemsFromWish =
        AddTournamentItemsFromWish(
            tournamentId = tournamentId,
            itemIds = itemIds,
        )
}
