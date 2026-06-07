package com.depromeet.piki.tournament.service

import com.depromeet.piki.item.domain.ItemSnapshot
import com.depromeet.piki.item.repository.ItemRepository
import com.depromeet.piki.item.repository.ItemSnapshotRepository
import com.depromeet.piki.tournament.domain.Tournament
import com.depromeet.piki.tournament.domain.TournamentHistory
import com.depromeet.piki.tournament.domain.TournamentItem
import com.depromeet.piki.tournament.domain.TournamentStatus
import com.depromeet.piki.tournament.domain.TournamentUser
import com.depromeet.piki.tournament.event.TournamentItemAdded
import com.depromeet.piki.tournament.event.TournamentJoined
import com.depromeet.piki.tournament.event.TournamentStarted
import com.depromeet.piki.tournament.repository.TournamentItemRepository
import com.depromeet.piki.tournament.repository.TournamentRepository
import com.depromeet.piki.tournament.repository.TournamentUserRepository
import com.depromeet.piki.tournament.service.dto.AddTournamentItemsFromWish
import com.depromeet.piki.tournament.service.dto.CreateTournament
import com.depromeet.piki.tournament.service.dto.CreateTournamentResult
import com.depromeet.piki.tournament.service.dto.GroupResult
import com.depromeet.piki.tournament.service.dto.GroupResultItem
import com.depromeet.piki.tournament.service.dto.ParticipantSummary
import com.depromeet.piki.tournament.service.dto.PlayLinkInfo
import com.depromeet.piki.tournament.service.dto.RankedItem
import com.depromeet.piki.tournament.service.dto.RecordMatch
import com.depromeet.piki.tournament.service.dto.TournamentDetail
import com.depromeet.piki.tournament.service.dto.TournamentInvitePreview
import com.depromeet.piki.tournament.service.dto.TournamentItemDetail
import com.depromeet.piki.tournament.service.dto.TournamentStartResult
import com.depromeet.piki.tournament.service.dto.TournamentSummary
import com.depromeet.piki.user.repository.UserRepository
import com.depromeet.piki.wishlist.repository.WishRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class TournamentService(
    private val tournamentUserRepository: TournamentUserRepository,
    private val tournamentRepository: TournamentRepository,
    private val tournamentItemRepository: TournamentItemRepository,
    private val userRepository: UserRepository,
    private val itemRepository: ItemRepository,
    private val itemSnapshotRepository: ItemSnapshotRepository,
    private val wishRepository: WishRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun create(
        userId: UUID,
        command: CreateTournament,
    ): CreateTournamentResult {
        val inviteCode = generateUniqueInviteCode()
        val inviteExpiresAt = LocalDateTime
            .now()
            .plusMinutes(command.inviteDurationMinutes)
        val tournament =
            tournamentRepository.saveTournament(
                Tournament(
                    ownerTournamentUserId = 0L,
                    name = command.name,
                    inviteCode = inviteCode,
                    inviteExpiresAt = inviteExpiresAt,
                ),
            )
        val tournamentUser =
            tournamentUserRepository.save(
                TournamentUser(tournamentId = tournament.getId(), userId = userId),
            )
        tournament.assignOwner(tournamentUser.getId())
        return CreateTournamentResult(
            tournamentId = tournament.getId(),
            inviteCode = inviteCode,
            inviteExpiresAt = inviteExpiresAt,
        )
    }

    @Transactional
    fun join(
        userId: UUID,
        tournamentId: Long,
        inviteCode: String?,
    ) {
        val tournament =
            tournamentRepository.findTournamentByIdForUpdate(tournamentId)
                ?: throw TournamentException.notFoundTournament()
        tournament.checkJoinable(inviteCode)
        tournamentUserRepository
            .findByTournamentIdAndUserId(tournamentId, userId)
            ?.let { throw TournamentException.alreadyParticipant() }
        if (tournamentUserRepository.countByTournamentId(tournamentId) >= TOURNAMENT_MAX_PARTICIPANT_COUNT) {
            throw TournamentException.participantLimitExceeded()
        }
        tournamentUserRepository.save(TournamentUser(tournamentId = tournamentId, userId = userId))
        // 참여가 커밋된 뒤에만 구독자에게 전달되도록 트랜잭션 안에서 발행한다 (롤백 시 미발행).
        eventPublisher.publishEvent(TournamentJoined(tournamentId = tournamentId, actorId = userId))
    }

    @Transactional
    fun addItemsFromWish(
        userId: UUID,
        command: AddTournamentItemsFromWish,
    ): List<Long> {
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
        val requestedItemIds = command.itemIds.toSet()
        if (requestedItemIds.size != command.itemIds.size) throw TournamentException.duplicateTournamentItem()
        val wishCount = wishRepository.countByItemIdsAndUserId(command.itemIds, userId)
        if (wishCount < command.itemIds.size) throw TournamentException.itemNotInWishlist()
        if (requestedItemIds.any { it in existingItemIds }) throw TournamentException.duplicateTournamentItem()
        if (existingItemIds.size + command.itemIds.size > TOURNAMENT_MAX_ITEM_COUNT) {
            throw TournamentException.tooManyTournamentItems()
        }
        val foundItems = itemRepository.findByIds(command.itemIds)
        val foundItemIds = foundItems
            .map { it.getId() }
            .toSet()
        if (command.itemIds.any { it !in foundItemIds }) throw TournamentException.notFoundItems()
        // 출전 시점에 위시의 활성 snapshot 을 tournament_item 에 고정한다 — 이후 위시 갱신과 무관하게 그 버전을 본다.
        val snapshotIdByItemId =
            wishRepository
                .findByItemIdsAndUserId(command.itemIds, userId)
                .associate { it.itemId to it.snapshotId }
        // 비동기 파싱 중(PROCESSING)이거나 실패(FAILED)한 상품은 이름·가격이 비어 출전에 부적합하다. 활성 snapshot 이 READY 인 것만 허용.
        val activeSnapshots = itemSnapshotRepository.findByIds(snapshotIdByItemId.values.filterNotNull())
        if (activeSnapshots.size != command.itemIds.size || activeSnapshots.any { !it.isReady() }) {
            throw TournamentException.itemNotReady()
        }
        val savedItemIds = tournamentItemRepository
            .saveAll(
                command.itemIds.map { itemId ->
                    val snapshotId =
                        snapshotIdByItemId[itemId]
                            ?: error("wish 의 활성 snapshot 없음 — itemId=$itemId, userId=$userId")
                    TournamentItem(
                        tournamentId = command.tournamentId,
                        itemId = itemId,
                        userId = userId,
                        snapshotId = snapshotId,
                    )
                },
            )
            .map { it.getId() }
        // 여러 개를 한 번에 추가해도 "아이템이 추가됐다"는 사실은 1건이라 이벤트도 1회만 발행한다.
        eventPublisher.publishEvent(TournamentItemAdded(tournamentId = command.tournamentId, actorId = userId))
        return savedItemIds
    }

    @Transactional
    fun start(
        userId: UUID,
        tournamentId: Long,
    ): List<TournamentStartResult> {
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
        // 검증·표시값 모두 출전 시점 고정 snapshot 에서 본다.
        val snapshotById = snapshotsOf(tournamentItems)
        for (tournamentItem in tournamentItems) {
            val snapshot = tournamentItem.requireSnapshot(snapshotById)
            if (!snapshot.isReady()) throw TournamentException.itemNotReadyToStart()
            snapshot.currentPrice ?: throw TournamentException.itemPriceRequired()
        }

        tournament.start()
        // 시작이 커밋된 뒤에만 참가자에게 전달되도록 트랜잭션 안에서 발행한다 (롤백 시 미발행).
        eventPublisher.publishEvent(TournamentStarted(tournamentId = tournamentId, actorId = userId))
        return tournamentItems
            .map { tournamentItem ->
                val snapshot = tournamentItem.requireSnapshot(snapshotById)
                TournamentStartResult(
                    tournamentItemId = tournamentItem.getId(),
                    name = snapshot.name,
                    price = snapshot.currentPrice,
                    currency = snapshot.currency,
                    imageUrl = snapshot.imageUrl,
                )
            }
            .sortedWith(compareBy({ it.price }, { it.tournamentItemId }))
    }

    @Transactional(readOnly = true)
    fun getTournamentById(
        tournamentId: Long,
        userId: UUID,
    ): TournamentDetail {
        val tournament =
            tournamentRepository.findTournamentById(tournamentId)
                ?: throw TournamentException.notFoundTournament()
        val currentUser = tournamentUserRepository.findByTournamentIdAndUserId(tournamentId, userId)
            ?: throw TournamentException.forbiddenTournament()
        val isOwner = currentUser.getId() == tournament.ownerTournamentUserId

        return when (tournament.status) {
            TournamentStatus.PENDING -> {
                val tournamentItems = tournamentItemRepository.findAllByTournamentId(tournamentId)
                val snapshotById = snapshotsOf(tournamentItems)
                val tournamentUsers = tournamentUserRepository.findByTournamentId(tournamentId)
                val userById = userRepository
                    .findByIds(
                        tournamentUsers
                            .map { it.userId }
                            .toSet(),
                    )
                    .associateBy { it.id }
                TournamentDetail.Pending(
                    tournamentId = tournament.getId(),
                    name = tournament.name,
                    inviteCode = tournament.inviteCode,
                    inviteExpiresAt = tournament.inviteExpiresAt,
                    items = tournamentItems.map { toItemDetail(it, snapshotById) },
                    participants =
                        tournamentUsers.mapNotNull { tu ->
                            userById[tu.userId]?.let { user ->
                                TournamentDetail.ParticipantDetail(
                                    userId = user.id,
                                    nickname = user.nickname,
                                    profileImage = user.profileImage,
                                    isWithdrawn = !user.isActive(),
                                )
                            }
                        },
                    isOwner = isOwner,
                )
            }

            TournamentStatus.IN_PROGRESS -> {
                val histories = tournamentRepository.findTournamentHistoriesByTournamentId(tournamentId)
                // 히스토리는 currentRound ASC, id ASC 정렬이라 lastOrNull()은 라운드가 바뀌면 틀림 — ID 최대값이 가장 최근 매치
                val lastHistory = histories
                    .maxByOrNull { it.getId() }
                    ?.let { TournamentDetail.HistoryEntry.from(it) }
                val allTournamentItems = tournamentItemRepository.findAllByTournamentId(tournamentId)
                val currentRound = computeExpectedRound(allTournamentItems.size, allTournamentItems.size / 2, histories)
                // 단일 패스: 탈락 아이템 + 현재 라운드 대결 완료 아이템 동시 수집
                val eliminatedItemIds = mutableSetOf<Long>()
                val foughtInCurrentRoundIds = mutableSetOf<Long>()
                for (h in histories) {
                    eliminatedItemIds.add(h.loser())
                    if (h.currentRound == currentRound) {
                        foughtInCurrentRoundIds.add(h.firstTournamentItemId)
                        foughtInCurrentRoundIds.add(h.secondTournamentItemId)
                    }
                }
                // 생존 중(탈락 X) + 현재 라운드 미대결 아이템
                val remainingTournamentItems = allTournamentItems.filter { item ->
                    item.getId() !in eliminatedItemIds && item.getId() !in foughtInCurrentRoundIds
                }
                val snapshotById = snapshotsOf(remainingTournamentItems)
                val remainingItems = remainingTournamentItems
                    .map { toItemDetail(it, snapshotById) }
                    .sortedWith(compareBy({ it.price }, { it.tournamentItemId }))
                TournamentDetail.InProgress(
                    tournamentId = tournament.getId(),
                    name = tournament.name,
                    currentRound = currentRound,
                    lastHistory = lastHistory,
                    remainingItems = remainingItems,
                    isOwner = isOwner,
                )
            }

            TournamentStatus.COMPLETED -> {
                val histories = tournamentRepository.findTournamentHistoriesByTournamentId(tournamentId)
                buildCompleted(tournament, histories, computeHasGroupResult(tournament), isOwner)
            }
        }
    }

    @Transactional(readOnly = true)
    fun getTournamentItem(
        userId: UUID,
        tournamentId: Long,
        tournamentItemId: Long,
    ): TournamentItemDetail {
        tournamentRepository.findTournamentById(tournamentId)
            ?: throw TournamentException.notFoundTournament()
        tournamentUserRepository.findByTournamentIdAndUserId(tournamentId, userId)
            ?: throw TournamentException.forbiddenTournament()
        val tournamentItem = tournamentItemRepository.findById(tournamentItemId)
            ?: throw TournamentException.notFoundTournamentItem()
        if (tournamentItem.tournamentId != tournamentId) throw TournamentException.notFoundTournamentItem()
        // sourceUrl(상품 링크)은 정체성이라 item 에서, 표시값은 고정 snapshot 에서 읽는다.
        val item = itemRepository.findById(tournamentItem.itemId)
            ?: throw TournamentException.notFoundTournamentItem()
        val snapshot = tournamentItem.requireSnapshot(snapshotsOf(listOf(tournamentItem)))
        return TournamentItemDetail(
            tournamentItemId = tournamentItem.getId(),
            itemId = item.getId(),
            sourceUrl = item.link?.toString(),
            name = snapshot.name,
            imageUrl = snapshot.imageUrl,
            price = snapshot.currentPrice,
            currency = snapshot.currency,
            status = snapshot.status,
        )
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
    ): TournamentDetail.Completed? {
        val tournament =
            tournamentRepository.findTournamentByIdForUpdate(command.tournamentId)
                ?: throw TournamentException.notFoundTournament()
        if (!tournament.isInProgress()) throw TournamentException.notInProgressTournament()
        tournamentUserRepository.findByTournamentIdAndUserId(command.tournamentId, userId)
            ?: throw TournamentException.forbiddenTournament()
        if (command.selectedTournamentItemId != command.firstTournamentItemId &&
            command.selectedTournamentItemId != command.secondTournamentItemId
        ) {
            throw TournamentException.invalidWinner()
        }

        val tournamentItemIds = tournamentItemRepository
            .findIdsByTournamentId(command.tournamentId)
            .toSet()
        if (command.firstTournamentItemId !in tournamentItemIds ||
            command.secondTournamentItemId !in tournamentItemIds
        ) {
            throw TournamentException.invalidTournamentItem()
        }

        val histories = tournamentRepository.findTournamentHistoriesByTournamentId(command.tournamentId)
        val eliminatedItemIds = histories
            .map { it.loser() }
            .toSet()
        if (command.firstTournamentItemId in eliminatedItemIds || command.secondTournamentItemId in eliminatedItemIds) {
            throw TournamentException.eliminatedTournamentItem()
        }
        val firstRoundMatchCount = tournamentItemIds.size / 2
        val expectedRound = computeExpectedRound(tournamentItemIds.size, firstRoundMatchCount, histories)
        if (command.currentRound != expectedRound) throw TournamentException.invalidCurrentRound()

        val newHistory = TournamentHistory(
            tournamentId = command.tournamentId,
            currentRound = command.currentRound,
            firstTournamentItemId = command.firstTournamentItemId,
            secondTournamentItemId = command.secondTournamentItemId,
            selectedTournamentItemId = command.selectedTournamentItemId,
        )
        tournamentRepository.saveHistory(newHistory)

        if (!tournament.isFinalRound(command.currentRound)) return null

        tournament.complete()
        val tournamentUser = tournamentUserRepository.findByTournamentIdAndUserId(command.tournamentId, userId)
            ?: error("recordMatch 권한 확인 후 tournamentUser 없음 — tournamentId=${command.tournamentId}")
        val isOwner = tournamentUser.getId() == tournament.ownerTournamentUserId
        return buildCompleted(tournament, histories + newHistory, computeHasGroupResult(tournament), isOwner)
    }

    private fun computeHasGroupResult(tournament: Tournament): Boolean {
        val rootId = tournament.sourceTournamentId ?: tournament.getId()
        val completedClones = tournamentRepository
            .findBySourceTournamentId(rootId)
            .count { it.isCompleted() }
        tournament.sourceTournamentId ?: return completedClones >= 1
        val rootCompleted = tournamentRepository
            .findTournamentById(rootId)
            ?.isCompleted() == true
        return rootCompleted || completedClones >= 2
    }

    private fun buildCompleted(
        tournament: Tournament,
        histories: List<TournamentHistory>,
        hasGroupResult: Boolean,
        isOwner: Boolean,
    ): TournamentDetail.Completed {
        val rankedPairs = computeRanking(histories)
        val tournamentItemById = tournamentItemRepository
            .findByIds(rankedPairs.map { it.first })
            .associateBy { it.getId() }
        val snapshotById = snapshotsOf(tournamentItemById.values)
        return TournamentDetail.Completed(
            tournamentId = tournament.getId(),
            name = tournament.name,
            result = rankedPairs.map { (tournamentItemId, rank) ->
                val tournamentItem = tournamentItemById.getValue(tournamentItemId)
                val snapshot = tournamentItem.requireSnapshot(snapshotById)
                RankedItem(
                    rank = rank,
                    tournamentItemId = tournamentItemId,
                    itemId = tournamentItem.itemId,
                    name = snapshot.name,
                    price = snapshot.currentPrice,
                    currency = snapshot.currency,
                    imageUrl = snapshot.imageUrl,
                )
            },
            hasGroupResult = hasGroupResult,
            isOwner = isOwner,
            playLinkExpiresAt = tournament.playLinkExpiresAt,
        )
    }

    @Transactional
    fun deleteTournament(
        userId: UUID,
        tournamentId: Long,
    ) {
        val tournament =
            tournamentRepository.findTournamentByIdForUpdate(tournamentId)
                ?: throw TournamentException.notFoundTournament()
        val tournamentUser =
            tournamentUserRepository.findByTournamentIdAndUserId(tournamentId, userId)
                ?: throw TournamentException.forbiddenTournament()
        if (tournamentUser.getId() != tournament.ownerTournamentUserId) throw TournamentException.forbiddenTournament()
        if (tournament.isInProgress()) throw TournamentException.inProgressTournamentCannotBeDeleted()
        tournamentRepository.softDeleteHistoriesByTournamentId(tournamentId)
        tournamentItemRepository.softDeleteAllByTournamentId(tournamentId)
        tournamentUserRepository.softDeleteAllByTournamentId(tournamentId)
        tournament.softDelete()
    }

    @Transactional
    fun updateInviteExpiry(
        userId: UUID,
        tournamentId: Long,
        inviteDurationMinutes: Long,
    ): LocalDateTime {
        val tournament =
            tournamentRepository.findTournamentByIdForUpdate(tournamentId)
                ?: throw TournamentException.notFoundTournament()
        val tournamentUser =
            tournamentUserRepository.findByTournamentIdAndUserId(tournamentId, userId)
                ?: throw TournamentException.forbiddenTournament()
        if (tournamentUser.getId() != tournament.ownerTournamentUserId) throw TournamentException.forbiddenTournament()
        if (!tournament.isPending()) throw TournamentException.notPendingTournament()
        val newExpiresAt = LocalDateTime
            .now()
            .plusMinutes(inviteDurationMinutes)
        tournament.updateInviteExpiry(newExpiresAt)
        return newExpiresAt
    }

    @Transactional(readOnly = true)
    fun getInvitePreview(tournamentId: Long): TournamentInvitePreview {
        val tournament =
            tournamentRepository.findTournamentById(tournamentId)
                ?: throw TournamentException.notFoundTournament()
        tournament.checkJoinable(null)
        val itemCount = tournamentItemRepository.countByTournamentId(tournamentId)
        val participantCount = tournamentUserRepository.countByTournamentId(tournamentId)
        return TournamentInvitePreview(
            tournamentId = tournamentId,
            tournamentName = tournament.name,
            itemCount = itemCount,
            participantCount = participantCount,
        )
    }

    @Transactional(readOnly = true)
    fun getInvitePreviewByCode(code: String): TournamentInvitePreview {
        val tournament =
            tournamentRepository.findTournamentByInviteCode(code)
                ?: throw TournamentException.invalidInviteCode()
        tournament.checkJoinable(null)
        val itemCount = tournamentItemRepository.countByTournamentId(tournament.getId())
        val participantCount = tournamentUserRepository.countByTournamentId(tournament.getId())
        return TournamentInvitePreview(
            tournamentId = tournament.getId(),
            tournamentName = tournament.name,
            itemCount = itemCount,
            participantCount = participantCount,
        )
    }

    @Transactional
    fun createPlayLink(
        userId: UUID,
        tournamentId: Long,
    ): LocalDateTime {
        val tournament =
            tournamentRepository.findTournamentByIdForUpdate(tournamentId)
                ?: throw TournamentException.notFoundTournament()
        if (!tournament.isCompleted()) throw TournamentException.notCompletedTournament()
        val tournamentUser =
            tournamentUserRepository.findByTournamentIdAndUserId(tournamentId, userId)
                ?: throw TournamentException.forbiddenTournament()
        if (tournamentUser.getId() != tournament.ownerTournamentUserId) throw TournamentException.forbiddenTournament()
        tournament.sourceTournamentId?.let { throw TournamentException.clonedTournamentCannotSharePlayLink() }
        tournament.playLinkExpiresAt?.let { throw TournamentException.playLinkAlreadyCreated() }
        val expiresAt = LocalDateTime
            .now()
            .plusDays(PLAY_LINK_DURATION_DAYS)
        tournament.createPlayLink(expiresAt)
        return expiresAt
    }

    @Transactional(readOnly = true)
    fun getPlayLinkInfo(tournamentId: Long): PlayLinkInfo {
        val tournament =
            tournamentRepository.findTournamentById(tournamentId)
                ?: throw TournamentException.notFoundTournament()
        val expiresAt = tournament.playLinkExpiresAt ?: throw TournamentException.playLinkNotCreated()
        if (!tournament.isPlayLinkValid()) throw TournamentException.playLinkExpired()
        val itemCount = tournamentItemRepository.countByTournamentId(tournamentId)
        return PlayLinkInfo(
            sourceTournamentId = tournamentId,
            tournamentName = tournament.name,
            itemCount = itemCount,
            playLinkExpiresAt = expiresAt,
        )
    }

    @Transactional
    fun createFromPlayLink(
        userId: UUID,
        sourceTournamentId: Long,
    ): Long {
        val sourceTournament =
            tournamentRepository.findTournamentByIdForUpdate(sourceTournamentId)
                ?: throw TournamentException.notFoundTournament()
        sourceTournament.playLinkExpiresAt ?: throw TournamentException.playLinkNotCreated()
        if (!sourceTournament.isPlayLinkValid()) throw TournamentException.playLinkExpired()

        val existingTournamentIds = tournamentUserRepository
            .findTournamentIdsByUserId(userId)
            .toSet()
        val alreadyCloned = tournamentRepository
            .findBySourceTournamentId(sourceTournamentId)
            .any { it.getId() in existingTournamentIds }
        if (alreadyCloned) throw TournamentException.alreadyCloned()

        val sourceItems = tournamentItemRepository.findAllByTournamentId(sourceTournamentId)
        require(sourceItems.isNotEmpty()) { "플레이 링크 복제 시 원본 아이템 없음 — sourceTournamentId=$sourceTournamentId" }

        val inviteCode = generateUniqueInviteCode()
        val newTournament = tournamentRepository.saveTournament(
            Tournament(
                ownerTournamentUserId = 0L,
                name = sourceTournament.name,
                inviteCode = inviteCode,
                inviteExpiresAt = LocalDateTime
                    .now()
                    .plusMinutes(TOURNAMENT_INVITE_DEFAULT_DURATION_MINUTES),
                sourceTournamentId = sourceTournamentId,
            ),
        )
        val tournamentUser = tournamentUserRepository.save(
            TournamentUser(tournamentId = newTournament.getId(), userId = userId),
        )
        newTournament.assignOwner(tournamentUser.getId())

        // 플레이 링크 복제는 원본 브래킷을 그대로 옮기는 것이라, 원본 tournament_item 이 고정한 snapshot 도 같이 박는다.
        tournamentItemRepository.saveAll(
            sourceItems.map {
                TournamentItem(
                    tournamentId = newTournament.getId(),
                    itemId = it.itemId,
                    userId = userId,
                    snapshotId = it.snapshotId,
                )
            },
        )
        return newTournament.getId()
    }

    @Transactional(readOnly = true)
    fun getGroupResult(
        userId: UUID,
        tournamentId: Long,
    ): GroupResult {
        val tournament =
            tournamentRepository.findTournamentById(tournamentId)
                ?: throw TournamentException.notFoundTournament()
        if (!tournament.isCompleted()) throw TournamentException.groupResultNotAvailable()
        tournamentUserRepository.findByTournamentIdAndUserId(tournamentId, userId)
            ?: throw TournamentException.forbiddenTournament()

        val rootId = tournament.sourceTournamentId ?: tournamentId
        val allRelated = buildList {
            tournament.sourceTournamentId
                ?.let {
                    tournamentRepository
                        .findTournamentById(rootId)
                        ?.let { root -> add(root) }
                }
                ?: add(tournament)
            addAll(tournamentRepository.findBySourceTournamentId(rootId))
        }.filter { it.isCompleted() }

        val ownerByTournamentId: Map<Long, UUID> = run {
            val ownerTuIds = allRelated
                .map { it.ownerTournamentUserId }
                .toSet()
            tournamentUserRepository
                .findByTournamentIds(allRelated.map { it.getId() })
                .filter { it.getId() in ownerTuIds }
                .associate { it.tournamentId to it.userId }
        }
        val userById = userRepository
            .findByIds(ownerByTournamentId.values.toSet())
            .associateBy { it.id }

        // 각 토너먼트의 결과를 itemId + rank 로 집계
        data class RankKey(val itemId: Long, val rank: Int)

        val chosenByMap = mutableMapOf<RankKey, MutableList<ParticipantSummary>>()

        // 원본 토너먼트의 아이템 정보를 기준으로 결과 표시
        val referenceItemsById: MutableMap<Long, RankedItem> = mutableMapOf()

        val allRelatedIds = allRelated.map { it.getId() }
        val historiesByTournamentId = tournamentRepository
            .findHistoriesByTournamentIds(allRelatedIds)
            .groupBy { it.tournamentId }
        val rankedByTournamentId = historiesByTournamentId
            .mapValues { (_, histories) -> computeRanking(histories) }
        val allTournamentItemIds = rankedByTournamentId.values
            .flatten()
            .map { it.first }
        val tItemById = tournamentItemRepository
            .findByIds(allTournamentItemIds)
            .associateBy { it.getId() }
        val snapshotById = snapshotsOf(tItemById.values)

        for (t in allRelated) {
            val ranked = rankedByTournamentId[t.getId()] ?: continue
            val ownerUserId = ownerByTournamentId[t.getId()] ?: continue
            val user = userById[ownerUserId] ?: continue
            val participant =
                ParticipantSummary(
                    userId = user.id,
                    nickname = user.nickname,
                    profileImage = user.profileImage,
                    isWithdrawn = !user.isActive(),
                )

            for ((tournamentItemId, rank) in ranked) {
                // tItem 누락은 삭제된 출전 아이템이 history 에 남은 정상 경우라 건너뛴다. 그러나 tItem 이 살아있으면
                // snapshot 은 불변식상 반드시 있어야 한다 — 없으면 continue 로 삼키지 않고 fail-fast 로 터뜨려, 부분 집계된
                // 랭킹이 200 으로 새어 나가는 것을 막는다.
                val tItem = tItemById[tournamentItemId] ?: continue
                val snapshot =
                    tItem.snapshotId?.let { snapshotById[it] }
                        ?: error("tournamentItem $tournamentItemId 의 snapshot ${tItem.snapshotId} 가 없다")
                val key = RankKey(tItem.itemId, rank)
                chosenByMap
                    .getOrPut(key) { mutableListOf() }
                    .add(participant)
                if (t.getId() == rootId) {
                    referenceItemsById[tItem.itemId] = RankedItem(
                        rank = rank,
                        tournamentItemId = tournamentItemId,
                        itemId = tItem.itemId,
                        name = snapshot.name,
                        price = snapshot.currentPrice,
                        currency = snapshot.currency,
                        imageUrl = snapshot.imageUrl,
                    )
                }
            }
        }

        val items = referenceItemsById.values
            .sortedBy { it.rank }
            .map { ref ->
                val key = RankKey(ref.itemId, ref.rank)
                GroupResultItem(
                    rank = ref.rank,
                    itemId = ref.itemId,
                    name = ref.name,
                    price = ref.price,
                    currency = ref.currency,
                    imageUrl = ref.imageUrl,
                    chosenBy = chosenByMap[key] ?: emptyList(),
                )
            }
        return GroupResult(items = items)
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

        val deleted = tournamentItemRepository.softDeleteIfPending(tournamentItemId, tournamentId)
        if (deleted == 0) throw TournamentException.notPendingTournament()
    }

    private fun toItemDetail(
        tournamentItem: TournamentItem,
        snapshotById: Map<Long, ItemSnapshot>,
    ): TournamentDetail.ItemDetail {
        val snapshot = tournamentItem.requireSnapshot(snapshotById)
        return TournamentDetail.ItemDetail(
            tournamentItemId = tournamentItem.getId(),
            itemId = tournamentItem.itemId,
            name = snapshot.name,
            price = snapshot.currentPrice,
            currency = snapshot.currency,
            imageUrl = snapshot.imageUrl,
            status = snapshot.status,
        )
    }

    // tournament_item 들이 고정한 snapshot 을 한 번에 조회해 id→snapshot 맵으로. 표시값 조회의 메모리 조인 재료다.
    private fun snapshotsOf(tournamentItems: Collection<TournamentItem>): Map<Long, ItemSnapshot> =
        itemSnapshotRepository
            .findByIds(tournamentItems.mapNotNull { it.snapshotId })
            .associateBy { it.getId() }

    // 고정 snapshot 은 출전 시점에 반드시 박힌다. 없으면 영속화 경로가 깨진 코드 버그다(전환 후 신규 출전부터 보장).
    private fun TournamentItem.requireSnapshot(snapshotById: Map<Long, ItemSnapshot>): ItemSnapshot =
        snapshotId?.let { snapshotById[it] }
            ?: error("snapshot 없음 — tournamentItemId=${getId()}, snapshotId=$snapshotId")

    private fun computeRanking(histories: List<TournamentHistory>): List<Pair<Long, Int>> {
        val finalMatch = histories.find { it.currentRound == Tournament.FINAL_ROUND_SIZE }
            ?: error("COMPLETED 상태인데 결승 기록 없음 — tournamentId=${histories.firstOrNull()?.tournamentId}")
        val semiRound = histories
            .filter { it.currentRound > Tournament.FINAL_ROUND_SIZE }
            .minByOrNull { it.currentRound }?.currentRound
        val semiLosers = semiRound
            ?.let { round ->
                histories
                    .filter { it.currentRound == round }
                    .map { it.loser() }
                    .sorted()
            }
            ?: emptyList()
        return buildList {
            add(finalMatch.selectedTournamentItemId to 1)
            add(finalMatch.loser() to 2)
            semiLosers.forEachIndexed { i, id -> add(id to 3 + i) }
        }
    }

    private fun TournamentHistory.loser(): Long =
        when (selectedTournamentItemId) {
            firstTournamentItemId -> secondTournamentItemId
            secondTournamentItemId -> firstTournamentItemId
            else -> error(
                "잘못된 tournament history: selectedTournamentItemId=$selectedTournamentItemId, " +
                    "firstTournamentItemId=$firstTournamentItemId, secondTournamentItemId=$secondTournamentItemId, " +
                    "tournamentId=$tournamentId",
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
        val countByRound = histories
            .groupingBy { it.currentRound }
            .eachCount()
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
        error("모든 라운드가 완료됐는데 IN_PROGRESS 상태임 tournamentId=${histories.firstOrNull()?.tournamentId}")
    }

    // invite_code 는 랜덤 생성이라 충돌 가능성이 낮지만 0이 아니다. 활성 코드 중복을 사전 확인하고
    // 충돌 시 재시도한다. DB 레벨 unique constraint(uk_tournaments_active_invite_code)가 최후 보루.
    private fun generateUniqueInviteCode(): String {
        repeat(INVITE_CODE_MAX_ATTEMPTS) {
            val code = Tournament.generateInviteCode()
            if (!tournamentRepository.existsTournamentByInviteCode(code)) return code
        }
        error("invite_code $INVITE_CODE_MAX_ATTEMPTS 회 생성 실패 — DB 포화 또는 keyspace 고갈 가능성")
    }
}

// 두 서비스(TournamentService.join, TournamentSocialPersistenceService.createGuestAndJoin)가
// 공유하는 초대 참여 검증. 링크 접근은 inviteCode=null, 코드 입력 경로는 inviteCode 포함.
internal fun Tournament.checkJoinable(inviteCode: String?) {
    if (!isPending()) throw TournamentException.notPendingTournament()
    if (!isInviteValid()) throw TournamentException.inviteExpired()
    inviteCode?.let { if (this.inviteCode != it) throw TournamentException.invalidInviteCode() }
}
