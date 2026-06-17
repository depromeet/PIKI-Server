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
        val isOwner: Boolean,
        val isRoot: Boolean,
        // ROOT 가 IN_PROGRESS 로 전환됐으나 이 멤버는 아직 매치를 시작하지 않은 상태.
        // true 이면 클라이언트가 "주최자가 시작했습니다, 지금 시작하세요" UI 를 보여야 한다.
        val ownerStarted: Boolean = false,
        val sourceTournamentId: Long? = null,
    ) : TournamentDetail()

    data class InProgress(
        val tournamentId: Long,
        val name: String,
        val currentRound: Int,
        val lastHistory: HistoryEntry?,
        val remainingItems: List<ItemDetail>,
        val isOwner: Boolean,
        val isRoot: Boolean,
        val sourceTournamentId: Long? = null,
    ) : TournamentDetail()

    data class Completed(
        val tournamentId: Long,
        val name: String,
        val result: List<RankedItem>,
        val hasGroupResult: Boolean,
        val isOwner: Boolean,
        val isRoot: Boolean,
        val playLinkExpiresAt: java.time.LocalDateTime?,
        val sourceTournamentId: Long? = null,
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
        // 탈퇴 유저 여부. 익명화된 닉네임·프로필 대신 FE 가 이 플래그로 "유저 알수없음" 을 렌더한다.
        val isWithdrawn: Boolean,
        val itemCount: Int,
    )
}
