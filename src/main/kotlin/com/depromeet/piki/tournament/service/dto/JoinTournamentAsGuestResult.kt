package com.depromeet.piki.tournament.service.dto

import com.depromeet.piki.auth.service.dto.TokenPair
import com.depromeet.piki.user.domain.User

data class JoinTournamentAsGuestResult(
    val tokenPair: TokenPair,
    val user: User,
    val tournamentId: Long,
)
