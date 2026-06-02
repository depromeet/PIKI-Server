package com.depromeet.piki.tournament.service

import com.depromeet.piki.auth.service.AuthService
import com.depromeet.piki.tournament.service.dto.JoinTournamentAsGuestResult
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class TournamentInviteService(
    private val tournamentService: TournamentService,
    private val persistenceService: TournamentSocialPersistenceService,
    private val authService: AuthService,
) {
    fun join(
        userId: UUID,
        tournamentId: Long,
        inviteCode: String?,
    ) = tournamentService.join(userId, tournamentId, inviteCode)

    fun joinAsGuest(
        tournamentId: Long,
        inviteCode: String?,
        nickname: String,
    ): JoinTournamentAsGuestResult {
        val user = persistenceService.createGuestAndJoin(tournamentId, inviteCode, nickname)
        val tokenPair = authService.createTokensForUser(user)
        return JoinTournamentAsGuestResult(
            tokenPair = tokenPair,
            user = user,
            tournamentId = tournamentId,
        )
    }
}
