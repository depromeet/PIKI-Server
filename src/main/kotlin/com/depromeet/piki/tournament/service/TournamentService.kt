package com.depromeet.piki.tournament.service

import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.item.repository.ItemRepository
import com.depromeet.piki.tournament.domain.Tournament
import com.depromeet.piki.tournament.domain.TournamentBracket
import com.depromeet.piki.tournament.domain.TournamentHistory
import com.depromeet.piki.tournament.domain.TournamentItem
import com.depromeet.piki.tournament.domain.TournamentStatus
import com.depromeet.piki.tournament.domain.TournamentUser
import com.depromeet.piki.tournament.repository.TournamentItemRepository
import com.depromeet.piki.tournament.repository.TournamentRepository
import com.depromeet.piki.tournament.repository.TournamentUserRepository
import com.depromeet.piki.tournament.service.dto.AddTournamentItemsFromWish
import com.depromeet.piki.tournament.service.dto.CreateTournament
import com.depromeet.piki.tournament.service.dto.RecordMatch
import com.depromeet.piki.tournament.service.dto.TournamentBracketResult
import com.depromeet.piki.tournament.service.dto.TournamentDetail
import com.depromeet.piki.tournament.service.dto.TournamentSummary
import com.depromeet.piki.user.repository.UserRepository
import com.depromeet.piki.wishlist.repository.WishRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class TournamentService(
    private val tournamentUserRepository: TournamentUserRepository,
    private val tournamentRepository: TournamentRepository,
    private val tournamentItemRepository: TournamentItemRepository,
    private val userRepository: UserRepository,
    private val itemRepository: ItemRepository,
    private val wishRepository: WishRepository,
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
    fun addItemsFromWish(
        userId: UUID,
        command: AddTournamentItemsFromWish,
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
        // 요청 내 중복 확인 — wishCount 는 unique itemId 기준이라 먼저 걸러야 정확하다
        if (command.itemIds.toSet().size != command.itemIds.size) throw TournamentException.duplicateTournamentItem()
        val wishCount = wishRepository.countByItemIdsAndUserId(command.itemIds, userId)
        if (wishCount < command.itemIds.size) throw TournamentException.itemNotInWishlist()
        if (command.itemIds.any { it in existingItemIds }) throw TournamentException.duplicateTournamentItem()
        if (existingItemIds.size + command.itemIds.size > TOURNAMENT_MAX_ITEM_COUNT) {
            throw TournamentException.tooManyTournamentItems()
        }
        val foundItems = itemRepository.findByIds(command.itemIds)
        val foundItemIds = foundItems.map { it.getId() }.toSet()
        if (command.itemIds.any { it !in foundItemIds }) throw TournamentException.notFoundItems()
        // 비동기 파싱 중(PROCESSING)이거나 실패(FAILED)한 상품은 이름·가격이 비어 출전에 부적합하다. READY 만 허용.
        if (foundItems.any { !it.isReady() }) throw TournamentException.itemNotReady()
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
    ): TournamentBracketResult {
        val tournament =
            tournamentRepository.findTournamentById(tournamentId)
                ?: throw TournamentException.notFoundTournament()
        if (!tournament.isPending()) throw TournamentException.notPendingTournament()
        val owner =
            tournamentUserRepository.findByTournamentIdAndUserId(tournamentId, userId)
                ?: throw TournamentException.forbiddenTournament()
        if (owner.getId() != tournament.ownerTournamentUserId) throw TournamentException.forbiddenTournament()
        val tournamentItems = tournamentItemRepository.findAllByTournamentId(tournamentId)
        if (tournamentItems.size !in
            TOURNAMENT_MIN_ITEM_COUNT..TOURNAMENT_MAX_ITEM_COUNT
        ) {
            throw TournamentException.invalidItemCount()
        }
        val itemById =
            itemRepository
                .findByIds(tournamentItems.map { it.itemId })
                .associate { it.getId() to it }
        if (tournamentItems.any { it.itemId !in itemById }) throw TournamentException.notFoundItems()
        if (itemById.values.any { !it.isReady() }) throw TournamentException.itemNotReadyToStart()
        val itemsWithPrice = tournamentItems.map { it.getId() to itemById[it.itemId]?.currentPrice }
        val bracket = TournamentBracket.generate(itemsWithPrice)

        val itemDetailsByTournamentItemId =
            tournamentItems.associate { tournamentItem ->
                val item = itemById[tournamentItem.itemId]
                tournamentItem.getId() to
                    TournamentBracketResult.ItemDetail(
                        name = item?.name,
                        price = item?.currentPrice,
                        currency = item?.currency,
                        imageUrl = item?.imageUrl,
                    )
            }
        tournament.start()
        return TournamentBracketResult(bracket, itemDetailsByTournamentItemId)
    }

    @Transactional(readOnly = true)
    fun getTournamentById(
        tournamentId: Long,
        userId: UUID,
    ): TournamentDetail {
        val tournament =
            tournamentRepository.findTournamentById(tournamentId)
                ?: throw TournamentException.notFoundTournament()
        tournamentUserRepository.findByTournamentIdAndUserId(tournamentId, userId)
            ?: throw TournamentException.forbiddenTournament()

        return when (tournament.status) {
            TournamentStatus.PENDING -> {
                val tournamentItems = tournamentItemRepository.findAllByTournamentId(tournamentId)
                val itemById =
                    itemRepository
                        .findByIds(tournamentItems.map { it.itemId })
                        .associate { it.getId() to it }
                val tournamentUsers = tournamentUserRepository.findByTournamentId(tournamentId)
                val userById = userRepository.findByIds(tournamentUsers.map { it.userId }.toSet()).associateBy { it.id }
                TournamentDetail.Pending(
                    tournamentId = tournament.getId(),
                    name = tournament.name,
                    items = tournamentItems.map { toItemDetail(it, itemById) },
                    participants =
                        tournamentUsers.mapNotNull { tu ->
                            userById[tu.userId]?.let { user ->
                                TournamentDetail.ParticipantDetail(
                                    userId = user.id,
                                    nickname = user.nickname,
                                    profileImage = user.profileImage,
                                )
                            }
                        },
                )
            }

            // TODO: IN_PROGRESS 조회 구현
            TournamentStatus.IN_PROGRESS -> error("IN_PROGRESS 조회 미구현")

            // TODO: COMPLETED 조회 구현
            TournamentStatus.COMPLETED -> error("COMPLETED 조회 미구현")
        }
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
        val userIds =
            tournamentUsers
                .map { it.userId }
                .toSet()
        val profileImageByUserId =
            userRepository
                .findByIds(userIds)
                .associate { it.id to it.profileImage }
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

        val histories = tournamentRepository.findTournamentHistoriesByTournamentId(command.tournamentId)
        val firstRoundMatchCount = tournamentItemIds.size / 2
        val expectedRound = computeExpectedRound(tournamentItemIds.size, firstRoundMatchCount, histories)
        if (command.currentRound != expectedRound) throw TournamentException.invalidCurrentRound()

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

    private fun toItemDetail(
        tournamentItem: TournamentItem,
        itemById: Map<Long, Item>,
    ): TournamentDetail.ItemDetail {
        val item = itemById[tournamentItem.itemId]
        return TournamentDetail.ItemDetail(
            tournamentItemId = tournamentItem.getId(),
            itemId = tournamentItem.itemId,
            name = item?.name,
            price = item?.currentPrice,
            currency = item?.currency,
            imageUrl = item?.imageUrl,
        )
    }

    // 완료된 라운드 수와 첫 라운드 매치 수를 기반으로 다음 진행해야 할 라운드를 계산한다.
    // currentPlayers = 해당 라운드 시작 시 남은 플레이어 수 = currentRound 값과 동일.
    // nextPlayers = currentPlayers - played: 승자 수 + 부전승(bye) 수로 홀수 아이템의 leftover 를 자연스럽게 흡수한다.
    private fun computeExpectedRound(
        startRound: Int,
        firstRoundMatchCount: Int,
        histories: List<TournamentHistory>,
    ): Int {
        val countByRound = histories.groupingBy { it.currentRound }.eachCount()
        var currentPlayers = startRound
        var matchesExpected = firstRoundMatchCount
        while (currentPlayers >= Tournament.FINAL_ROUND_SIZE) {
            val played = countByRound[currentPlayers] ?: 0
            if (played < matchesExpected) return currentPlayers
            if (currentPlayers == Tournament.FINAL_ROUND_SIZE) break
            val nextPlayers = currentPlayers - played
            matchesExpected = nextPlayers / 2
            currentPlayers = nextPlayers
        }
        // 모든 라운드가 완료됐는데 isInProgress() 인 상태 — tournament.complete() 누락 버그
        error("recordMatch: 모든 라운드가 완료됐는데 IN_PROGRESS 상태임 tournamentId=${histories.firstOrNull()?.tournamentId}")
    }

}
