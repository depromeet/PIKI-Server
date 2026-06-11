package com.depromeet.piki.tournament.controller.dto

import jakarta.validation.constraints.Pattern

data class JoinTournamentRequest(
    @field:Pattern(regexp = "[A-Z]{3}\\d{3}", message = "초대 코드 형식이 올바르지 않아요.")
    val inviteCode: String?,
)
