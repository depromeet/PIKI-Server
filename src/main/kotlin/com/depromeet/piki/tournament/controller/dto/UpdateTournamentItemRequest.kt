package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.tournament.service.dto.UpdateTournamentItem
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

data class UpdateTournamentItemRequest(
    @field:Size(min = 1, max = 512)
    val name: String?,
    @field:Min(0)
    val price: Int?,
    @field:Size(max = 8)
    val currency: String?,
    @field:Size(max = 2048)
    val imageUrl: String?,
) {
    fun toUpdateTournamentItem(
        tournamentId: Long,
        tournamentItemId: Long,
    ): UpdateTournamentItem =
        UpdateTournamentItem(
            tournamentId = tournamentId,
            tournamentItemId = tournamentItemId,
            name = name,
            price = price,
            currency = currency,
            imageUrl = imageUrl,
        )
}
