package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.tournament.service.dto.GroupResult
import com.fasterxml.jackson.annotation.JsonInclude
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GroupResultResponse(
    val items: List<GroupResultItemResponse>,
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class GroupResultItemResponse(
        val rank: Int,
        val itemId: Long,
        val name: String?,
        val price: Int?,
        val currency: String?,
        val imageUrl: String?,
        val chosenBy: List<ParticipantSummaryResponse>,
    )

    data class ParticipantSummaryResponse(
        val userId: UUID,
        val nickname: String,
        val profileImage: String,
        // 탈퇴 유저면 true. 닉네임·프로필이 익명값이라 FE 가 이 플래그로 "유저 알수없음" 을 렌더한다.
        val isWithdrawn: Boolean,
    )

    companion object {
        fun from(result: GroupResult): GroupResultResponse =
            GroupResultResponse(
                items = result.items.map { item ->
                    GroupResultItemResponse(
                        rank = item.rank,
                        itemId = item.itemId,
                        name = item.name,
                        price = item.price,
                        currency = item.currency,
                        imageUrl = item.imageUrl,
                        chosenBy = item.chosenBy.map { p ->
                            ParticipantSummaryResponse(
                                userId = p.userId,
                                nickname = p.nickname,
                                profileImage = p.profileImage,
                                isWithdrawn = p.isWithdrawn,
                            )
                        },
                    )
                },
            )
    }
}
