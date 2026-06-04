package com.depromeet.piki.tournament.controller.dto

import jakarta.validation.constraints.Pattern

data class JoinTournamentRequest(
    @field:Pattern(regexp = "[A-Z]{3}\\d{3}", message = "초대 코드는 영어 대문자 3자리 + 숫자 3자리 형식이어야 합니다.")
    val inviteCode: String?,
)
