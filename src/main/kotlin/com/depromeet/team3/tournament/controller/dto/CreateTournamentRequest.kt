package com.depromeet.team3.tournament.controller.dto

import com.depromeet.team3.tournament.service.dto.CreateTournament
import jakarta.validation.constraints.NotBlank

data class CreateTournamentRequest(
    @field:NotBlank
    val name: String,
) {
    fun toCreateTournament(): CreateTournament = CreateTournament(name = name)
}
