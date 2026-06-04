package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.tournament.service.dto.PlayLinkInfo
import java.time.LocalDateTime

data class PlayLinkInfoResponse(
    val sourceTournamentId: Long,
    val tournamentName: String,
    val itemCount: Int,
    val playLinkExpiresAt: LocalDateTime,
) {
    companion object {
        fun from(info: PlayLinkInfo): PlayLinkInfoResponse =
            PlayLinkInfoResponse(
                sourceTournamentId = info.sourceTournamentId,
                tournamentName = info.tournamentName,
                itemCount = info.itemCount,
                playLinkExpiresAt = info.playLinkExpiresAt,
            )
    }
}
