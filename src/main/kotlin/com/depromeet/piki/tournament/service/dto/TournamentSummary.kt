package com.depromeet.piki.tournament.service.dto

import com.depromeet.piki.tournament.domain.Tournament
import com.depromeet.piki.tournament.domain.TournamentStatus
import java.time.LocalDateTime

data class TournamentSummary(
    val tournamentId: Long,
    val name: String,
    val status: TournamentStatus,
    val createdAt: LocalDateTime,
    val participantProfileImages: List<String>,
) {
    companion object {
        fun of(
            tournament: Tournament,
            participantProfileImages: List<String>,
            effectiveStatus: TournamentStatus = tournament.status,
        ): TournamentSummary =
            TournamentSummary(
                tournamentId = tournament.getId(),
                name = tournament.name,
                status = effectiveStatus,
                createdAt = tournament.createdAt,
                participantProfileImages = participantProfileImages,
            )
    }
}
