package com.depromeet.piki.tournament.service.dto

import java.time.LocalDateTime

data class CreateTournament(
    val name: String,
    val inviteExpiresAt: LocalDateTime = LocalDateTime.now().plusMinutes(30),
)
