package com.depromeet.piki.tournament.controller.dto

import com.depromeet.piki.auth.service.dto.TokenPair
import com.depromeet.piki.auth.web.TokenCarrying
import com.depromeet.piki.tournament.service.dto.JoinTournamentAsGuestResult
import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class JoinTournamentAsGuestResponse(
    val userId: UUID,
    val nickname: String,
    val profileImage: String,
    val tournamentId: Long,
    @get:JsonIgnore
    override val tokenPair: TokenPair,
    @get:Schema(description = "액세스 토큰 (APP=값 / WEB=null, 쿠키로 전달)", nullable = true, example = "eyJhbGciOiJIUzI1NiJ9...")
    val accessToken: String? = tokenPair.accessToken,
    @get:Schema(description = "리프레시 토큰 (APP=값 / WEB=null, 쿠키로 전달)", nullable = true, example = "eyJhbGciOiJIUzI1NiJ9...")
    val refreshToken: String? = tokenPair.refreshToken,
) : TokenCarrying {
    override fun withoutBodyTokens(): TokenCarrying = copy(accessToken = null, refreshToken = null)

    companion object {
        fun from(result: JoinTournamentAsGuestResult): JoinTournamentAsGuestResponse =
            JoinTournamentAsGuestResponse(
                tokenPair = result.tokenPair,
                userId = result.user.id,
                nickname = result.user.nickname,
                profileImage = result.user.profileImage,
                tournamentId = result.tournamentId,
            )
    }
}
