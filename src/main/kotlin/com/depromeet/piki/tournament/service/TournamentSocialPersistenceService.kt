package com.depromeet.piki.tournament.service

import com.depromeet.piki.tournament.domain.TournamentUser
import com.depromeet.piki.tournament.event.TournamentJoined
import com.depromeet.piki.tournament.repository.TournamentRepository
import com.depromeet.piki.tournament.repository.TournamentUserRepository
import com.depromeet.piki.user.domain.User
import com.depromeet.piki.user.service.UserService
import java.util.UUID
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 게스트 유저 생성과 토너먼트 참여를 하나의 트랜잭션으로 묶는다.
// TournamentInviteService 의 joinAsGuest 는 JWT 발급(외부 호출)을 트랜잭션 밖에서 해야 하므로,
// 영속화만 이 빈에 위임한다.
@Service
class TournamentSocialPersistenceService(
    private val tournamentRepository: TournamentRepository,
    private val tournamentUserRepository: TournamentUserRepository,
    private val userService: UserService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun createGuestAndJoin(
        tournamentId: Long,
        inviteCode: String?,
        nickname: String,
    ): User {
        val tournament =
            tournamentRepository.findTournamentByIdForUpdate(tournamentId)
                ?: throw TournamentException.notFoundTournament()
        tournament.checkJoinable(inviteCode)
        if (tournamentUserRepository.countByTournamentId(tournamentId) >= TOURNAMENT_MAX_PARTICIPANT_COUNT) {
            throw TournamentException.participantLimitExceeded()
        }
        val user = userService.createGuestWithNickname(nickname)
        tournamentUserRepository.save(TournamentUser(tournamentId = tournamentId, userId = user.id))
        // 게스트 합류. 트랜잭션 안에서 발행 → AFTER_COMMIT 후 알림. 수신자는 기존 참가자 - 본인(actor=새 게스트).
        eventPublisher.publishEvent(TournamentJoined(tournamentId = tournamentId, actorId = user.id))
        return user
    }

    @Transactional
    fun compensateGuestJoin(tournamentId: Long, userId: UUID) {
        tournamentUserRepository.softDeleteByTournamentIdAndUserId(tournamentId, userId)
        userService.softDelete(userId)
    }
}
