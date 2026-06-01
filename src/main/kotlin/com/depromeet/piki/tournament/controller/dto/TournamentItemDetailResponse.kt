package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.item.domain.ItemStatus
import com.depromeet.piki.tournament.service.dto.TournamentItemDetail
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TournamentItemDetailResponse(
    val tournamentItemId: Long,
    val itemId: Long,
    val sourceUrl: String?,
    val name: String?,
    val imageUrl: String?,
    val price: Int?,
    val currency: String?,
    val status: ItemStatus,
) {
    companion object {
        fun from(detail: TournamentItemDetail): TournamentItemDetailResponse =
            TournamentItemDetailResponse(
                tournamentItemId = detail.tournamentItemId,
                itemId = detail.itemId,
                sourceUrl = detail.sourceUrl,
                name = detail.name,
                imageUrl = detail.imageUrl,
                price = detail.price,
                currency = detail.currency,
                status = detail.status,
            )
    }
}
