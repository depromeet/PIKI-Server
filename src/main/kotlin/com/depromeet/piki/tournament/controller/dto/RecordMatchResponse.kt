package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.tournament.service.dto.RecordMatchResult

data class RecordMatchResponse(
    val result: List<RankedItemResponse>,
) {
    companion object {
        fun from(result: RecordMatchResult): RecordMatchResponse =
            RecordMatchResponse(result = result.rankedItems.map { RankedItemResponse.from(it) })
    }
}
