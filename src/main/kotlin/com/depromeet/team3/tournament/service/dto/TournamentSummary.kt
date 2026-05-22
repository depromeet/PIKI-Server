package com.depromeet.team3.tournament.service.dto

import com.depromeet.team3.tournament.domain.Tournament
import com.depromeet.team3.tournament.domain.TournamentStatus
import java.time.LocalDateTime

data class TournamentSummary(
    val tournamentId: Long,
    val name: String,
    val status: TournamentStatus,
    val updatedAt: LocalDateTime,
    val participantProfileImages: List<String>,
) {
    companion object {
        fun of(
            tournament: Tournament,
            participantProfileImages: List<String>,
        ): TournamentSummary =
            TournamentSummary(
                tournamentId = tournament.getId(),
                name = tournament.name,
                status = tournament.status,
                updatedAt = tournament.updatedAt,
                participantProfileImages = participantProfileImages,
            )
    }
}
