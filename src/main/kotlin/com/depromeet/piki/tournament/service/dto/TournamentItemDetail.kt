package com.depromeet.piki.tournament.service.dto

import com.depromeet.piki.item.domain.ItemStatus

data class TournamentItemDetail(
    val tournamentItemId: Long,
    val itemId: Long,
    val name: String?,
    val imageUrl: String?,
    val price: Int?,
    val currency: String?,
    val status: ItemStatus,
)
