package com.depromeet.piki.tournament.controller.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class JoinTournamentAsGuestRequest(
    @field:Pattern(regexp = "[A-Z]{3}\\d{3}", message = "초대 코드는 영어 대문자 3자리 + 숫자 3자리 형식이어야 합니다.")
    val inviteCode: String?,
    @field:NotBlank(message = "닉네임은 비어 있을 수 없습니다.")
    @field:Size(max = 10, message = "닉네임은 10자 이하여야 합니다.")
    val nickname: String,
)
