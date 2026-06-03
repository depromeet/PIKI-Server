package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.tournament.service.dto.CreateTournament
import jakarta.validation.constraints.NotBlank

data class CreateTournamentRequest(
    @field:NotBlank(message = "토너먼트 이름은 필수입니다.")
    val name: String,
) {
    fun toCreateTournament(): CreateTournament = CreateTournament(name = name)
}
