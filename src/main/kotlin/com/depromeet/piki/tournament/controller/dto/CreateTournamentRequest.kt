package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.tournament.service.TOURNAMENT_INVITE_DEFAULT_DURATION_MINUTES
import com.depromeet.piki.tournament.service.TOURNAMENT_INVITE_MAX_DURATION_MINUTES
import com.depromeet.piki.tournament.service.dto.CreateTournament
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime

data class CreateTournamentRequest(
    @field:NotBlank
    val name: String,
    @field:Min(1)
    @field:Max(TOURNAMENT_INVITE_MAX_DURATION_MINUTES)
    val inviteDurationMinutes: Long? = null,
) {
    fun toCreateTournament(): CreateTournament {
        val duration = inviteDurationMinutes ?: TOURNAMENT_INVITE_DEFAULT_DURATION_MINUTES
        return CreateTournament(
            name = name,
            inviteExpiresAt = LocalDateTime.now().plusMinutes(duration),
        )
    }
}
