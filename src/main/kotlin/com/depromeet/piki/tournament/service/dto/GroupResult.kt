package com.depromeet.piki.tournament.service.dto

import java.util.UUID

data class GroupResult(
    val items: List<GroupResultItem>,
)

data class GroupResultItem(
    val rank: Int,
    val itemId: Long,
    val name: String?,
    val price: Int?,
    val currency: String?,
    val imageUrl: String?,
    val chosenBy: List<ParticipantSummary>,
)

data class ParticipantSummary(
    val userId: UUID,
    val nickname: String,
    val profileImage: String,
)
