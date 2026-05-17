package com.depromeet.team3.tournament.service

import com.depromeet.team3.tournament.domain.Tournament
import com.depromeet.team3.tournament.domain.TournamentHistory
import com.depromeet.team3.tournament.domain.TournamentItem
import com.depromeet.team3.tournament.domain.TournamentUser
import com.depromeet.team3.tournament.repository.TournamentRepository
import com.depromeet.team3.tournament.repository.TournamentUserRepository
import com.depromeet.team3.tournament.service.dto.AddTournamentItems
import com.depromeet.team3.tournament.service.dto.CreateTournament
import com.depromeet.team3.tournament.service.dto.RecordMatch
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
    fun create(userId: UUID, command: CreateTournament): Long {
        val tournamentId = tournamentRepository.saveTournament(
            Tournament(ownerTournamentUserId = userId, name = command.name),
        )

    @Transactional
    fun addItems(userId: UUID, command: AddTournamentItems) {
        val tournament = tournamentRepository.findTournamentById(command.tournamentId)
            ?: throw TournamentException.notFoundTournament()
        if (tournament.ownerTournamentUserId != userId) throw TournamentException.forbiddenTournament()
        if (!tournament.isPending()) throw TournamentException.notPendingTournament()
        val ownedCount = wishRepository.countByIdsAndGuestId(command.itemIds, userId)
        if (ownedCount != command.itemIds.size.toLong()) throw WishException.forbiddenWishItems()
        tournamentRepository.saveTournamentItems(
            command.itemIds.map { itemId -> TournamentItem(tournamentId = command.tournamentId, itemId = itemId, userId = userId) },
        )
    }

    @Transactional
    fun start(userId: UUID, tournamentId: Long) {
        val tournament = tournamentRepository.findTournamentById(tournamentId)
            ?: throw TournamentException.notFoundTournament()
        if (tournament.ownerTournamentUserId != userId) throw TournamentException.forbiddenTournament()
        if (!tournament.isPending()) throw TournamentException.notPendingTournament()
        tournament.start()
    }

    @Transactional(readOnly = true)
    fun getTournamentById(tournamentId: Long, userId: UUID): TournamentInfo {
        val tournament = tournamentRepository.findTournamentById(tournamentId)
            ?: throw TournamentException.notFoundTournament()
        if (tournament.ownerTournamentUserId != userId) throw TournamentException.forbiddenTournament()
        val items = tournamentRepository.findTournamentItemsByTournamentId(tournamentId)
        val histories = tournamentRepository.findTournamentHistoriesByTournamentId(tournamentId)
        return TournamentInfo.of(tournament, items, histories)
    }

    @Transactional
    fun recordMatch(userId: UUID, command: RecordMatch) {
        val tournament = tournamentRepository.findTournamentById(command.tournamentId)
            ?: throw TournamentException.notFoundTournament()

        if (tournament.ownerTournamentUserId != userId) throw TournamentException.forbiddenTournament()
        if (!tournament.isInProgress()) throw TournamentException.notInProgressTournament()
        if (command.selectedTournamentItemId !in setOf(command.firstItemId, command.secondItemId)) {
            throw TournamentException.invalidWinner()
        }

        val tournamentItemIds = tournamentRepository
            .findTournamentItemsByTournamentId(command.tournamentId)
            .mapTo(mutableSetOf()) { it.getId() }
        if (command.firstItemId !in tournamentItemIds || command.secondItemId !in tournamentItemIds) {
            throw TournamentException.invalidTournamentItem()
        }

        tournamentRepository.saveHistory(
            TournamentHistory(
                tournamentId = command.tournamentId,
                currentRound = command.currentRound,
                firstTournamentItemId = command.firstItemId,
                secondTournamentItemId = command.secondItemId,
                selectedTournamentItemId = command.selectedTournamentItemId,
            ),
        )

        if (tournament.isFinalRound(command.currentRound)) {
            tournament.complete()
        }
    }
}
