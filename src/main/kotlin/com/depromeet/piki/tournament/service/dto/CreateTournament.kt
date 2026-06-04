package com.depromeet.piki.tournament.service.dto

import com.depromeet.piki.tournament.service.TOURNAMENT_INVITE_DEFAULT_DURATION_MINUTES

data class CreateTournament(
    val name: String,
    val inviteDurationMinutes: Long = TOURNAMENT_INVITE_DEFAULT_DURATION_MINUTES,
)
