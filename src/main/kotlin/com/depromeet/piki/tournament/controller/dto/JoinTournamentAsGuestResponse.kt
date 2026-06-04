package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.tournament.service.dto.JoinTournamentAsGuestResult
import java.util.UUID

data class JoinTournamentAsGuestResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: UUID,
    val nickname: String,
    val profileImage: String,
    val tournamentId: Long,
) {
    companion object {
        fun from(result: JoinTournamentAsGuestResult): JoinTournamentAsGuestResponse =
            JoinTournamentAsGuestResponse(
                accessToken = result.tokenPair.accessToken,
                refreshToken = result.tokenPair.refreshToken,
                userId = result.user.id,
                nickname = result.user.nickname,
                profileImage = result.user.profileImage,
                tournamentId = result.tournamentId,
            )
    }
}
