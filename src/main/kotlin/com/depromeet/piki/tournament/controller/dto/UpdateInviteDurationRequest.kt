package com.depromeet.piki.tournament.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Future
import java.time.LocalDateTime

data class UpdateInviteDurationRequest(
    @field:Schema(description = "새 초대 마감 시각 (현재 시각 이후, 최대 24시간 이내)", example = "2026-06-18T15:30:00")
    @field:Future(message = INVITE_EXPIRY_PAST_MESSAGE)
    val newExpiresAt: LocalDateTime,
) {
    companion object {
        const val INVITE_EXPIRY_PAST_MESSAGE = "초대 마감 시각은 현재 시각 이후로 입력해 주세요."
    }
}
