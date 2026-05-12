package com.depromeet.team3.tournament.controller.dto

import com.depromeet.team3.tournament.service.dto.StartTournament
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class StartTournamentRequest(
    @field:NotBlank
    val name: String,
    @field:Min(2)
    val round: Int,
    @field:NotEmpty
    @field:Size(min = 2)
    val wishItemIds: List<Long>,
) {
    fun toStartTournament(): StartTournament =
        StartTournament(
            name = name,
            round = round,
            wishItemIds = wishItemIds,
        )
}
