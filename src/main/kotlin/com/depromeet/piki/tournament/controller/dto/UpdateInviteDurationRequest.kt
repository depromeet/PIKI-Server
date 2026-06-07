package com.depromeet.piki.tournament.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class UpdateInviteDurationRequest(
    @field:Schema(description = "새 초대 마감까지 남은 시간 (분 단위, 1-1440)", example = "60")
    @field:Min(value = 1, message = INVITE_DURATION_MIN_MESSAGE)
    @field:Max(value = 1440, message = INVITE_DURATION_MAX_MESSAGE)
    val inviteDurationMinutes: Long,
) {
    companion object {
        const val INVITE_DURATION_MIN_MESSAGE = "초대 유효 시간은 1분 이상이어야 합니다."
        const val INVITE_DURATION_MAX_MESSAGE = "초대 유효 시간은 1440분(24시간) 이하이어야 합니다."
    }
}
