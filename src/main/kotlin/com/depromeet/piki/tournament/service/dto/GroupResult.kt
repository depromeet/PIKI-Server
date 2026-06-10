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
    // 탈퇴 유저 여부. 닉네임·프로필은 익명값으로 가려지므로, FE 가 "유저 알수없음" 을 깔끔히 렌더하도록 명시 플래그를 내린다.
    val isWithdrawn: Boolean,
)
