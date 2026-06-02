package com.depromeet.piki.tournament.service.dto

import com.depromeet.piki.item.domain.ItemStatus
import com.depromeet.piki.tournament.domain.TournamentHistory
import java.util.UUID

sealed class TournamentDetail {
    data class Pending(
        val tournamentId: Long,
        val name: String,
                                                                                                                                        val inviteCode: String,
        val inviteExpiresAt: java.time.LocalDateTime,
        val items: List<ItemDetail>,
        val participants: List<ParticipantDetail>,
    ) : TournamentDetail()

    data class InProgress(
        val tournamentId: Long,
        val name: String,
        val currentRound: Int,
        val lastHistory: HistoryEntry?,
        val remainingItems: List<ItemDetail>,
    ) : TournamentDetail()

    data class Completed(
        val tournamentId: Long,
        val name: String,
        val result: List<RankedItem>,
        val hasGroupResult: Boolean,
    ) : TournamentDetail()

    data class ItemDetail(
        val tournamentItemId: Long,
        val itemId: Long,
        val name: String?,
        val price: Int?,
        val currency: String?,
        val imageUrl: String?,
        val status: ItemStatus,
    )

    data class HistoryEntry(
        val currentRound: Int,
        val firstTournamentItemId: Long,
        val secondTournamentItemId: Long,
        val selectedTournamentItemId: Long,
    ) {
        companion object {
            fun from(history: TournamentHistory): HistoryEntry =
                HistoryEntry(
                    currentRound = history.currentRound,
                    firstTournamentItemId = history.firstTournamentItemId,
                    secondTournamentItemId = history.secondTournamentItemId,
                    selectedTournamentItemId = history.selectedTournamentItemId,
                )
        }
    }

    data class ParticipantDetail(
        val userId: UUID,
        val nickname: String,
        val profileImage: String,
    )
}
