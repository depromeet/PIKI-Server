package com.depromeet.team3.tournament.service

import com.depromeet.team3.tournament.domain.Tournament
import com.depromeet.team3.tournament.domain.TournamentHistory
import com.depromeet.team3.tournament.repository.TournamentRepository
import com.depromeet.team3.tournament.service.dto.RecordMatch
import com.depromeet.team3.tournament.service.dto.StartTournament
import com.depromeet.team3.tournament.service.dto.TournamentInfo
import com.depromeet.team3.wishlist.repository.WishRepository
import com.depromeet.team3.wishlist.service.WishException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class TournamentService(
    private val tournamentRepository: TournamentRepository,
    private val wishRepository: WishRepository,
) {
    @Transactional
    fun start(
        userId: UUID,
        command: StartTournament,
    ): Long {
        val ownedCount = wishRepository.countByIdsAndGuestId(command.wishItemIds, userId)
        if (ownedCount != command.wishItemIds.size.toLong()) throw WishException.forbiddenWishItems()
        return tournamentRepository.saveTournament(
            Tournament(
                userId = userId,
                name = command.name,
                round = command.round,
                wishItemIds = command.wishItemIds,
            ),
        )
    }

    @Transactional(readOnly = true)
    fun getTournamentById(
        tournamentId: Long,
        userId: UUID,
    ): TournamentInfo {
        val tournament =
            tournamentRepository.findTournamentById(tournamentId)
                ?: throw TournamentException.notFoundTournament()
        if (tournament.userId != userId) throw TournamentException.forbiddenTournament()
        val histories = tournamentRepository.findTournamentHistoriesByTournamentId(tournamentId)
        return TournamentInfo.of(tournament, histories)
    }

    @Transactional
    fun recordMatch(
        userId: UUID,
        command: RecordMatch,
    ) {
        val tournament =
            tournamentRepository.findTournamentById(command.tournamentId)
                ?: throw TournamentException.notFoundTournament()

        if (tournament.userId != userId) throw TournamentException.forbiddenTournament()
        if (tournament.isCompleted()) throw TournamentException.alreadyCompleted()
        if (command.winnerWishItemId !in setOf(command.firstWishItemId, command.secondWishItemId)) {
            throw TournamentException.invalidWinner()
        }

        tournamentRepository.saveHistory(
            TournamentHistory(
                tournamentId = command.tournamentId,
                currentRound = command.currentRound,
                firstWishItemId = command.firstWishItemId,
                secondWishItemId = command.secondWishItemId,
                winnerWishItemId = command.winnerWishItemId,
            ),
        )

        if (tournament.isFinalRound(command.currentRound)) {
            tournament.complete(command.winnerWishItemId)
        }
    }
}
