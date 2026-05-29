package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.tournament.service.dto.RankedItem
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RankedItemResponse(
    val rank: Int,
    val tournamentItemId: Long,
    val itemId: Long,
    val name: String?,
    val price: Int?,
    val currency: String?,
    val imageUrl: String?,
) {
    companion object {
        fun from(r: RankedItem): RankedItemResponse =
            RankedItemResponse(r.rank, r.tournamentItemId, r.itemId, r.name, r.price, r.currency, r.imageUrl)
    }
}
