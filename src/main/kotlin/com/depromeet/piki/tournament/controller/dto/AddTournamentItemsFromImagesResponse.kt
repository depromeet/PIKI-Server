package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.tournament.service.dto.AddTournamentItemsFromImagesResult

data class AddTournamentItemsFromImagesResponse(
    val failedCount: Int,
    val failedIndices: List<Int>,
) {
    companion object {
        fun from(result: AddTournamentItemsFromImagesResult): AddTournamentItemsFromImagesResponse =
            AddTournamentItemsFromImagesResponse(
                failedCount = result.failedIndices.size,
                failedIndices = result.failedIndices,
            )
    }
}
