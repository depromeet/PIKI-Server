package com.depromeet.piki.tournament.repository

import com.depromeet.piki.tournament.domain.TournamentUser
import java.time.LocalDateTime
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class TournamentUserRepositoryImpl(
    private val tournamentUserJpaRepository: TournamentUserJpaRepository,
) : TournamentUserRepository {
    override fun save(tournamentUser: TournamentUser): TournamentUser = tournamentUserJpaRepository.save(tournamentUser)

    override fun findByTournamentIdAndUserId(
        tournamentId: Long,
        userId: UUID,
    ): TournamentUser? = tournamentUserJpaRepository.findByTournamentIdAndUserIdAndDeletedAtIsNull(tournamentId, userId)

    override fun findTournamentIdsByUserId(userId: UUID): List<Long> =
        tournamentUserJpaRepository.findTournamentIdsByUserId(userId)

    override fun findByTournamentId(tournamentId: Long): List<TournamentUser> =
        tournamentUserJpaRepository.findByTournamentIdAndDeletedAtIsNull(tournamentId)

    override fun countByTournamentId(tournamentId: Long): Int =
        tournamentUserJpaRepository.countByTournamentIdAndDeletedAtIsNull(tournamentId)

    override fun findByTournamentIds(tournamentIds: List<Long>): List<TournamentUser> =
        if (tournamentIds.isEmpty()) {
            emptyList()
        } else {
            tournamentUserJpaRepository.findByTournamentIdInAndNotDeleted(tournamentIds)
        }

    override fun softDeleteAllByTournamentId(tournamentId: Long) {
        tournamentUserJpaRepository.softDeleteAllByTournamentId(tournamentId, LocalDateTime.now())
    }
}
