package com.depromeet.team3.tournament.controller.dto

import com.depromeet.team3.tournament.domain.TournamentStatus
import com.depromeet.team3.tournament.service.dto.TournamentSummary
import java.time.LocalDateTime

data class TournamentSummaryResponse(
    val tournamentId: Long,
    val name: String,
    val status: TournamentStatus,
    val updatedAt: LocalDateTime,
    val participantProfileImages: List<String>,
) {
    companion object {
        fun from(summary: TournamentSummary): TournamentSummaryResponse =
            TournamentSummaryResponse(
                tournamentId = summary.tournamentId,
                name = summary.name,
                status = summary.status,
                updatedAt = summary.updatedAt,
                participantProfileImages = summary.participantProfileImages,
            )
    }
}
