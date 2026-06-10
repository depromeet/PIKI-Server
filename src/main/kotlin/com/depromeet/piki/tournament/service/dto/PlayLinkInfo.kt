package com.depromeet.piki.tournament.service.dto

import java.time.LocalDateTime

data class PlayLinkInfo(
    val sourceTournamentId: Long,
    val tournamentName: String,
    val itemCount: Int,
    val playLinkExpiresAt: LocalDateTime,
)
