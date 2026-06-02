package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.tournament.service.dto.CreateTournamentResult
import java.time.LocalDateTime

data class CreateTournamentResponse(
    val tournamentId: Long,
    val inviteCode: String,
    val inviteExpiresAt: LocalDateTime,
) {
    companion object {
        fun from(result: CreateTournamentResult): CreateTournamentResponse =
            CreateTournamentResponse(
                tournamentId = result.tournamentId,
                inviteCode = result.inviteCode,
                inviteExpiresAt = result.inviteExpiresAt,
            )
    }
}
