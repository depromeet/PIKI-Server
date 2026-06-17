package com.depromeet.piki.tournament.controller.dto

import jakarta.validation.constraints.Pattern

data class JoinTournamentRequest(
    @field:Pattern(regexp = "[A-Z]{3}\\d{3}", message = JoinTournamentAsGuestRequest.INVITE_CODE_PATTERN_MESSAGE)
    val inviteCode: String?,
)
