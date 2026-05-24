package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.tournament.service.dto.TournamentBracketResult

data class TournamentBracketResponse(val matches: List<MatchResponse>) {
    data class MatchResponse(
        val firstItem: MatchItemResponse,
        val secondItem: MatchItemResponse,
    )

    data class MatchItemResponse(
        val tournamentItemId: Long,
        val name: String?,
        val price: Int?,
        val currency: String?,
        val imageUrl: String?,
    ) {
        companion object {
            fun from(tournamentItemId: Long, detail: TournamentBracketResult.ItemDetail?): MatchItemResponse =
                MatchItemResponse(
                    tournamentItemId = tournamentItemId,
                    name = detail?.name,
                    price = detail?.price,
                    currency = detail?.currency,
                    imageUrl = detail?.imageUrl,
                )
        }
    }

    companion object {
        fun from(result: TournamentBracketResult): TournamentBracketResponse =
            TournamentBracketResponse(
                matches = result.bracket.matches.map { match ->
                    MatchResponse(
                        firstItem = MatchItemResponse.from(
                            match.firstTournamentItemId,
                            result.itemDetailsByTournamentItemId[match.firstTournamentItemId],
                        ),
                        secondItem = MatchItemResponse.from(
                            match.secondTournamentItemId,
                            result.itemDetailsByTournamentItemId[match.secondTournamentItemId],
                        ),
                    )
                },
            )
    }
}
