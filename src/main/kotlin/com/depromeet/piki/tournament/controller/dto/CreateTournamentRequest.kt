package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.tournament.service.TOURNAMENT_INVITE_DEFAULT_DURATION_MINUTES
import com.depromeet.piki.tournament.service.TOURNAMENT_INVITE_MAX_DURATION_MINUTES
import com.depromeet.piki.tournament.service.dto.CreateTournament
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class CreateTournamentRequest(
    @field:NotBlank(message = "토너먼트 이름은 필수입니다.")
    val name: String,
    @field:Min(1)
    @field:Max(TOURNAMENT_INVITE_MAX_DURATION_MINUTES)
    val inviteDurationMinutes: Long? = null,
) {
    fun toCreateTournament(): CreateTournament =
        CreateTournament(
            name = name,
            inviteDurationMinutes = inviteDurationMinutes ?: TOURNAMENT_INVITE_DEFAULT_DURATION_MINUTES,
        )
}
