package com.depromeet.piki.tournament.controller.dto

import jakarta.validation.constraints.NotBlank

data class AddTournamentItemFromLinkRequest(
    @field:NotBlank
    val url: String,
)
