package com.depromeet.team3.tournament.service

import com.depromeet.team3.tournament.domain.Tournament
import com.depromeet.team3.tournament.domain.TournamentHistory
import com.depromeet.team3.tournament.domain.TournamentItem
import com.depromeet.team3.tournament.domain.TournamentUser
import com.depromeet.team3.tournament.repository.TournamentItemRepository
import com.depromeet.team3.tournament.repository.TournamentRepository
import com.depromeet.team3.tournament.repository.TournamentUserRepository
import com.depromeet.team3.tournament.service.dto.AddTournamentItem
import com.depromeet.team3.tournament.service.dto.CreateTournament
import com.depromeet.team3.tournament.service.dto.RecordMatch
import com.depromeet.team3.tournament.service.dto.TournamentInfo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class TournamentService(
    private val tournamentUserRepository: TournamentUserRepository,
    private val tournamentRepository: TournamentRepository,
    private val tournamentItemRepository: TournamentItemRepository,
) {
    @Transactional
    fun create(
        userId: UUID,
        command: CreateTournament,
    ): Long {
        val tournament =
            tournamentRepository.saveTournament(
                Tournament(ownerTournamentUserId = 0L, name = command.name),
            )
        val tournamentUser =
            tournamentUserRepository.save(
                TournamentUser(tournamentId = tournament.getId(), userId = userId),
            )
        tournament.assignOwner(tournamentUser.getId())
        return tournament.getId()
    }

    @Transactional
    fun addItem(
        userId: UUID,
        command: AddTournamentItem,
    ) {
        val tournament =
            tournamentRepository.findTournamentById(command.tournamentId)
                ?: throw TournamentException.notFoundTournament()
        if (!tournament.isPending()) throw TournamentException.notPendingTournament()
        val existingItemIds =
            tournamentItemRepository
                .findAllByTournamentId(command.tournamentId)
                .map { it.itemId }
                .toSet()
        if (existingItemIds.size >= MAX_ITEM_COUNT) throw TournamentException.maxItemCountExceeded()
        if (command.itemId in existingItemIds) throw TournamentException.duplicateTournamentItem()
        tournamentItemRepository.save(
            TournamentItem(tournamentId = command.tournamentId, itemId = command.itemId, userId = userId),
        )
    }

    @Transactional
    fun start(
        userId: UUID,
        tournamentId: Long,
    ) {
        val tournament =
            tournamentRepository.findTournamentById(tournamentId)
                ?: throw TournamentException.notFoundTournament()
        if (!tournament.isPending()) throw TournamentException.notPendingTournament()
        val owner =
            tournamentUserRepository.findByTournamentIdAndUserId(tournamentId, userId)
                ?: throw TournamentException.forbiddenTournament()
        if (owner.getId() != tournament.ownerTournamentUserId) throw TournamentException.forbiddenTournament()
        val itemCount = tournamentItemRepository.findAllByTournamentId(tournamentId).size
        if (itemCount !in MIN_ITEM_COUNT..MAX_ITEM_COUNT) throw TournamentException.invalidItemCount()
        tournament.start()
    }

    @Transactional(readOnly = true)
    fun getTournamentById(
        tournamentId: Long,
        userId: UUID,
    ): TournamentInfo {
        val tournament =
            tournamentRepository.findTournamentById(tournamentId)
                ?: throw TournamentException.notFoundTournament()
        tournamentUserRepository.findByTournamentIdAndUserId(tournamentId, userId)
            ?: throw TournamentException.forbiddenTournament()
        val items = tournamentItemRepository.findAllByTournamentId(tournamentId)
        val histories = tournamentRepository.findTournamentHistoriesByTournamentId(tournamentId)
        return TournamentInfo.of(tournament, items, histories)
    }

    @Transactional
    fun recordMatch(
        userId: UUID,
        command: RecordMatch,
    ) {
        val tournament =
            tournamentRepository.findTournamentById(command.tournamentId)
                ?: throw TournamentException.notFoundTournament()
        if (!tournament.isInProgress()) throw TournamentException.notInProgressTournament()
        tournamentUserRepository.findByTournamentIdAndUserId(command.tournamentId, userId)
            ?: throw TournamentException.forbiddenTournament()
        if (command.selectedTournamentItemId !in setOf(command.firstTournamentItemId, command.secondTournamentItemId)) {
            throw TournamentException.invalidWinner()
        }

        val tournamentItemIds =
            tournamentItemRepository
                .findAllByTournamentId(command.tournamentId)
                .mapTo(mutableSetOf()) { it.getId() }
        if (command.firstTournamentItemId !in tournamentItemIds ||
            command.secondTournamentItemId !in tournamentItemIds
        ) {
            throw TournamentException.invalidTournamentItem()
        }

        tournamentRepository.saveHistory(
            TournamentHistory(
                tournamentId = command.tournamentId,
                currentRound = command.currentRound,
                firstTournamentItemId = command.firstTournamentItemId,
                secondTournamentItemId = command.secondTournamentItemId,
                selectedTournamentItemId = command.selectedTournamentItemId,
            ),
        )

        if (tournament.isFinalRound(command.currentRound)) {
            tournament.complete()
        }
    }

    companion object {
        private const val MIN_ITEM_COUNT = 2
        private const val MAX_ITEM_COUNT = 32
    }
}
