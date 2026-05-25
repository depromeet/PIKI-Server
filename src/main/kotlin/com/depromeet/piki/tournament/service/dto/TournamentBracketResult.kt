package com.depromeet.piki.tournament.service.dto

import com.depromeet.piki.tournament.domain.TournamentBracket

data class TournamentBracketResult(
    val bracket: TournamentBracket,
    val itemDetailsByTournamentItemId: Map<Long, ItemDetail>,
) {
    data class ItemDetail(
        val name: String?,
        val price: Int?,
        val currency: String?,
        val imageUrl: String?,
    )
}
