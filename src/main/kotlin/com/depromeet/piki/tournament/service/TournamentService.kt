package com.depromeet.piki.tournament.service

import com.depromeet.piki.tournament.domain.Tournament
import com.depromeet.piki.tournament.domain.TournamentHistory
import com.depromeet.piki.tournament.domain.TournamentItem
import com.depromeet.piki.tournament.domain.TournamentStatus
import com.depromeet.piki.tournament.domain.TournamentUser
import com.depromeet.piki.tournament.repository.TournamentItemRepository
import com.depromeet.piki.tournament.repository.TournamentRepository
import com.depromeet.piki.tournament.repository.TournamentUserRepository
import com.depromeet.piki.tournament.service.dto.AddTournamentItems
import com.depromeet.piki.tournament.service.dto.CreateTournament
import com.depromeet.piki.tournament.service.dto.RecordMatch
import com.depromeet.piki.tournament.service.dto.TournamentInfo
import com.depromeet.piki.tournament.service.dto.TournamentSummary
import com.depromeet.piki.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class TournamentService(
    private val tournamentUserRepository: TournamentUserRepository,
    private val tournamentRepository: TournamentRepository,
    private val tournamentItemRepository: TournamentItemRepository,
    private val userRepository: UserRepository,
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
    fun addItems(
        userId: UUID,
        command: AddTournamentItems,
    ) {
        val tournament =
            tournamentRepository.findTournamentById(command.tournamentId)
                ?: throw TournamentException.notFoundTournament()
        if (!tournament.isPending()) throw TournamentException.notPendingTournament()
        tournamentUserRepository.findByTournamentIdAndUserId(command.tournamentId, userId)
            ?: throw TournamentException.forbiddenTournament()
        val existingItemIds =
            tournamentItemRepository
                .findAllByTournamentId(command.tournamentId)
                .map { it.itemId }
                .toSet()
        val hasDuplicate =
            command.itemIds.toSet().size != command.itemIds.size || command.itemIds.any { it in existingItemIds }
        if (hasDuplicate) throw TournamentException.duplicateTournamentItem()
        if (existingItemIds.size + command.itemIds.size >
            MAX_ITEM_COUNT
        ) {
            throw TournamentException.tooManyTournamentItems()
        }
        tournamentItemRepository.saveAll(
            command.itemIds.map { itemId ->
                TournamentItem(tournamentId = command.tournamentId, itemId = itemId, userId = userId)
            },
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

    @Transactional(readOnly = true)
    fun getTournaments(
        userId: UUID,
        statuses: List<TournamentStatus>?,
    ): List<TournamentSummary> {
        val tournamentIds = tournamentUserRepository.findTournamentIdsByUserId(userId)
        val tournaments = tournamentRepository.findByIdsAndStatuses(tournamentIds, statuses)
        if (tournaments.isEmpty()) return emptyList()

        val tournamentUsers = tournamentUserRepository.findByTournamentIds(tournaments.map { it.getId() })
        val userIds = tournamentUsers.map { it.userId }.toSet()
        val profileImageByUserId = userRepository.findByIds(userIds).associate { it.id to it.profileImage }
        val profileImagesByTournamentId =
            tournamentUsers
                .groupBy { it.tournamentId }
                .mapValues { (_, users) -> users.mapNotNull { profileImageByUserId[it.userId] } }

        return tournaments.map { tournament ->
            TournamentSummary.of(
                tournament = tournament,
                participantProfileImages = profileImagesByTournamentId[tournament.getId()] ?: emptyList(),
            )
        }
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

    @Transactional
    fun deleteItem(
        userId: UUID,
        tournamentId: Long,
        tournamentItemId: Long,
    ) {
        val tournament =
            tournamentRepository.findTournamentById(tournamentId)
                ?: throw TournamentException.notFoundTournament()
        if (!tournament.isPending()) throw TournamentException.notPendingTournament()
        val tournamentItem =
            tournamentItemRepository.findById(tournamentItemId)
                ?: throw TournamentException.notFoundTournamentItem()
        if (tournamentItem.tournamentId != tournamentId) throw TournamentException.notFoundTournamentItem()

        val isItemAdder = tournamentItem.userId == userId
        if (!isItemAdder) {
            val isTournamentOwner =
                tournamentUserRepository
                    .findByTournamentIdAndUserId(tournamentId, userId)
                    ?.getId() == tournament.ownerTournamentUserId
            if (!isTournamentOwner) throw TournamentException.forbiddenTournament()
        }

        val deleted = tournamentItemRepository.deleteIfPending(tournamentItemId, tournamentId)
        if (deleted == 0) throw TournamentException.notPendingTournament()
    }

    companion object {
        private const val MIN_ITEM_COUNT = 2
        private const val MAX_ITEM_COUNT = 32
    }
}
