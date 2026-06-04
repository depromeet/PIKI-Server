package com.depromeet.piki.tournament.service.dto

data class TournamentInvitePreview(
    val tournamentId: Long,
    val tournamentName: String,
    val itemCount: Int,
    val participantCount: Int,
)
