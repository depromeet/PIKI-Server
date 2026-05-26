package com.depromeet.piki.user.controller.dto

import com.depromeet.piki.user.domain.User
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "유저 요약 정보 (dev용)")
data class DevUserSummaryResponse(
    @field:Schema(description = "유저 식별자", format = "uuid")
    val userId: UUID,
    @field:Schema(description = "닉네임")
    val nickname: String,
) {
    companion object {
        fun from(user: User): DevUserSummaryResponse =
            DevUserSummaryResponse(userId = user.id, nickname = user.nickname)
    }
}
