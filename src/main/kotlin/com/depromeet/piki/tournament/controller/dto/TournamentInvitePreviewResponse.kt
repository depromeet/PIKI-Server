package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.tournament.service.dto.TournamentInvitePreview

data class TournamentInvitePreviewResponse(
    val tournamentId: Long,
    val tournamentName: String,
    val itemCount: Int,
    val participantCount: Int,
) {
    companion object {
        fun from(preview: TournamentInvitePreview): TournamentInvitePreviewResponse =
            TournamentInvitePreviewResponse(
                tournamentId = preview.tournamentId,
                tournamentName = preview.tournamentName,
                itemCount = preview.itemCount,
                participantCount = preview.participantCount,
            )
    }
}
