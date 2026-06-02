package com.depromeet.piki.tournament.service.dto

import java.time.LocalDateTime

data class CreateTournamentResult(
    val tournamentId: Long,
    val inviteCode: String,
    val inviteExpiresAt: LocalDateTime,
)
