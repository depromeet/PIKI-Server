package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.tournament.domain.TournamentStatus
import com.depromeet.piki.tournament.service.dto.TournamentDetail
import com.fasterxml.jackson.annotation.JsonInclude
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TournamentDetailResponse(
    val tournamentId: Long,
    val name: String,
    val status: TournamentStatus,
    val pending: PendingData?,
    val inProgress: InProgressData?,
    val completed: CompletedData?,
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class ItemDetailResponse(
        val tournamentItemId: Long,
        val itemId: Long,
        val name: String?,
        val price: Int?,
        val currency: String?,
        val imageUrl: String?,
    ) {
        companion object {
            fun from(d: TournamentDetail.ItemDetail): ItemDetailResponse =
                ItemDetailResponse(d.tournamentItemId, d.itemId, d.name, d.price, d.currency, d.imageUrl)
        }
    }

    data class BracketMatchResponse(
        val firstItem: ItemDetailResponse,
        val secondItem: ItemDetailResponse,
    ) {
        companion object {
            fun from(m: TournamentDetail.BracketMatch): BracketMatchResponse =
                BracketMatchResponse(ItemDetailResponse.from(m.firstItem), ItemDetailResponse.from(m.secondItem))
        }
    }

    data class HistoryResponse(
        val currentRound: Int,
        val firstTournamentItemId: Long,
        val secondTournamentItemId: Long,
        val selectedTournamentItemId: Long,
    ) {
        companion object {
            fun from(h: TournamentDetail.HistoryEntry): HistoryResponse =
                HistoryResponse(h.currentRound, h.firstTournamentItemId, h.secondTournamentItemId, h.selectedTournamentItemId)
        }
    }

    data class ParticipantResponse(
        val userId: UUID,
        val nickname: String,
        val profileImage: String,
    ) {
        companion object {
            fun from(p: TournamentDetail.ParticipantDetail): ParticipantResponse =
                ParticipantResponse(p.userId, p.nickname, p.profileImage)
        }
    }

    data class PendingData(
        val items: List<ItemDetailResponse>,
        val participants: List<ParticipantResponse>,
    )

    data class InProgressData(
        val startRound: Int,
        val bracket: List<BracketMatchResponse>,
        val history: List<HistoryResponse>,
    )

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
            fun from(r: TournamentDetail.RankedItem): RankedItemResponse =
                RankedItemResponse(r.rank, r.tournamentItemId, r.itemId, r.name, r.price, r.currency, r.imageUrl)
        }
    }

    data class CompletedData(val result: List<RankedItemResponse>)

    companion object {
        fun from(detail: TournamentDetail): TournamentDetailResponse =
            when (detail) {
                is TournamentDetail.Pending ->
                    TournamentDetailResponse(
                        tournamentId = detail.tournamentId,
                        name = detail.name,
                        status = TournamentStatus.PENDING,
                        pending =
                            PendingData(
                                items = detail.items.map { ItemDetailResponse.from(it) },
                                participants = detail.participants.map { ParticipantResponse.from(it) },
                            ),
                        inProgress = null,
                        completed = null,
                    )

                is TournamentDetail.InProgress ->
                    TournamentDetailResponse(
                        tournamentId = detail.tournamentId,
                        name = detail.name,
                        status = TournamentStatus.IN_PROGRESS,
                        pending = null,
                        inProgress =
                            InProgressData(
                                startRound = detail.startRound,
                                bracket = detail.bracket.map { BracketMatchResponse.from(it) },
                                history = detail.history.map { HistoryResponse.from(it) },
                            ),
                        completed = null,
                    )

                is TournamentDetail.Completed ->
                    TournamentDetailResponse(
                        tournamentId = detail.tournamentId,
                        name = detail.name,
                        status = TournamentStatus.COMPLETED,
                        pending = null,
                        inProgress = null,
                        completed = CompletedData(detail.result.map { RankedItemResponse.from(it) }),
                    )
            }
    }
}
