package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.item.domain.ItemStatus
import com.depromeet.piki.tournament.domain.TournamentStatus
import com.depromeet.piki.tournament.service.dto.TournamentDetail
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDateTime
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TournamentDetailResponse(
    val tournamentId: Long,
    val name: String,
    val status: TournamentStatus,
    val isOwner: Boolean,
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
        val status: ItemStatus,
    ) {
        companion object {
            fun from(d: TournamentDetail.ItemDetail): ItemDetailResponse =
                ItemDetailResponse(d.tournamentItemId, d.itemId, d.name, d.price, d.currency, d.imageUrl, d.status)
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class PendingData(
        val inviteCode: String,
        val inviteExpiresAt: LocalDateTime,
        val items: List<ItemDetailResponse>,
        val participants: List<ParticipantResponse>,
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class InProgressData(
        val currentRound: Int,
        val lastHistory: HistoryResponse?,
        val remainingItems: List<ItemDetailResponse>,
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class CompletedData(
        val result: List<RankedItemResponse>,
        val hasGroupResult: Boolean,
        val playLinkExpiresAt: LocalDateTime?,
    ) {
        companion object {
            fun from(completed: TournamentDetail.Completed): CompletedData =
                CompletedData(
                    result = completed.result.map { RankedItemResponse.from(it) },
                    hasGroupResult = completed.hasGroupResult,
                    playLinkExpiresAt = completed.playLinkExpiresAt,
                )
        }
    }

    companion object {
        fun from(detail: TournamentDetail): TournamentDetailResponse =
            when (detail) {
                is TournamentDetail.Pending ->
                    TournamentDetailResponse(
                        tournamentId = detail.tournamentId,
                        name = detail.name,
                        status = TournamentStatus.PENDING,
                        isOwner = detail.isOwner,
                        pending =
                            PendingData(
                                inviteCode = detail.inviteCode,
                                inviteExpiresAt = detail.inviteExpiresAt,
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
                        isOwner = detail.isOwner,
                        pending = null,
                        inProgress =
                            InProgressData(
                                currentRound = detail.currentRound,
                                lastHistory = detail.lastHistory?.let { HistoryResponse.from(it) },
                                remainingItems = detail.remainingItems.map { ItemDetailResponse.from(it) },
                            ),
                        completed = null,
                    )

                is TournamentDetail.Completed ->
                    TournamentDetailResponse(
                        tournamentId = detail.tournamentId,
                        name = detail.name,
                        status = TournamentStatus.COMPLETED,
                        isOwner = detail.isOwner,
                        pending = null,
                        inProgress = null,
                        completed = CompletedData.from(detail),
                    )
            }
    }
}
