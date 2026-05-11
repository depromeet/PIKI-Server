package com.depromeet.team3.tournament.service

import com.depromeet.team3.tournament.domain.Tournament
import com.depromeet.team3.tournament.domain.TournamentHistory
import com.depromeet.team3.tournament.domain.TournamentItem
import com.depromeet.team3.tournament.repository.TournamentRepository
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
    fun create(userId: UUID, command: CreateTournament): Long =
        tournamentRepository.saveTournament(
            Tournament(userId = userId, name = command.name),
        )

    @Transactional
    fun addItems(userId: UUID, command: AddTournamentItems) {
        val tournament = tournamentRepository.findTournamentById(command.tournamentId)
            ?: throw TournamentException.notFoundTournament()
        if (tournament.userId != userId) throw TournamentException.forbiddenTournament()
        if (!tournament.isPending()) throw TournamentException.notPendingTournament()
        val ownedCount = wishRepository.countByIdsAndGuestId(command.itemIds, userId)
        if (ownedCount != command.itemIds.size.toLong()) throw WishException.forbiddenWishItems()
        tournamentRepository.saveTournamentItems(
            command.itemIds.map { itemId -> TournamentItem(tournamentId = command.tournamentId, itemId = itemId) },
        )
    }

    // 친구 공유 및 참여 기능 추가 시 소유권 검증 로직 제거 필요
    @Transactional
    fun start(userId: UUID, tournamentId: Long) {
        val tournament = tournamentRepository.findTournamentById(tournamentId)
            ?: throw TournamentException.notFoundTournament()
        if (tournament.userId != userId) throw TournamentException.forbiddenTournament()
        if (!tournament.isPending()) throw TournamentException.notPendingTournament()
        tournament.start()
    }

    @Transactional(readOnly = true)
    fun getTournamentById(tournamentId: Long, userId: UUID): TournamentInfo {
        val tournament = tournamentRepository.findTournamentById(tournamentId)
            ?: throw TournamentException.notFoundTournament()
        if (tournament.userId != userId) throw TournamentException.forbiddenTournament()
        val items = tournamentRepository.findTournamentItemsByTournamentId(tournamentId)
        val histories = tournamentRepository.findTournamentHistoriesByTournamentId(tournamentId)
        return TournamentInfo.of(tournament, items, histories)
    }

    @Transactional
    fun recordMatch(userId: UUID, command: RecordMatch) {
        val tournament = tournamentRepository.findTournamentById(command.tournamentId)
            ?: throw TournamentException.notFoundTournament()

        if (tournament.userId != userId) throw TournamentException.forbiddenTournament()
        if (!tournament.isInProgress()) throw TournamentException.notInProgressTournament()
        if (command.winnerItemId !in setOf(command.firstItemId, command.secondItemId)) {
            throw TournamentException.invalidWinner()
        }

        val tournamentItemIds = tournamentRepository.findTournamentItemsByTournamentId(command.tournamentId)
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
                winnerTournamentItemId = command.winnerItemId,
            ),
        )

        if (tournament.isFinalRound(command.currentRound)) {
            tournament.complete()
        }
    }
}
