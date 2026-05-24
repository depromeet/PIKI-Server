package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.tournament.domain.TournamentStatus
import com.depromeet.piki.tournament.service.dto.TournamentSummary
import java.time.LocalDateTime

data class TournamentSummaryResponse(
    val tournamentId: Long,
    val name: String,
    val status: TournamentStatus,
    val createdAt: LocalDateTime,
    val participantProfileImages: List<String>,
) {
    companion object {
        fun from(summary: TournamentSummary): TournamentSummaryResponse =
            TournamentSummaryResponse(
                tournamentId = summary.tournamentId,
                name = summary.name,
                status = summary.status,
                createdAt = summary.createdAt,
                participantProfileImages = summary.participantProfileImages,
            )
    }
}
