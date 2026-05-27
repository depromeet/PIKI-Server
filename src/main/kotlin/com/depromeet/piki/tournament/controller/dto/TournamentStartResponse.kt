package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.tournament.service.dto.TournamentStartResult

data class TournamentStartResponse(val items: List<ItemResponse>) {
    data class ItemResponse(
        val tournamentItemId: Long,
        val name: String?,
        val price: Int?,
        val currency: String?,
        val imageUrl: String?,
    )

    companion object {
        fun from(results: List<TournamentStartResult>): TournamentStartResponse =
            TournamentStartResponse(
                items = results.map { result ->
                    ItemResponse(
                        tournamentItemId = result.tournamentItemId,
                        name = result.name,
                        price = result.price,
                        currency = result.currency,
                        imageUrl = result.imageUrl,
                    )
                },
            )
    }
}
