package com.depromeet.piki.tournament.service

import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.item.domain.ItemSnapshot
import com.depromeet.piki.item.domain.ItemStatus
import com.depromeet.piki.item.repository.ItemRepository
import com.depromeet.piki.item.repository.ItemSnapshotRepository
import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.tournament.domain.TournamentItem
import com.depromeet.piki.tournament.event.TournamentItemAdded
import com.depromeet.piki.tournament.repository.TournamentItemRepository
import com.depromeet.piki.tournament.repository.TournamentRepository
import com.depromeet.piki.tournament.repository.TournamentUserRepository
import com.depromeet.piki.tournament.service.dto.PersistedTournamentItem
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// TournamentItemService 의 외부 호출(링크 추출·이미지 추출)이 트랜잭션 밖에 있도록
// 아이템 저장과 토너먼트 아이템 등록만 별도 빈으로 분리한다.
// 같은 빈에서 @Transactional 메서드를 직접 호출하면 Spring AOP proxy 를 거치지 않아 트랜잭션이 무력화된다.
//
// 2단계 쓰기 이중화: item 을 저장/전이하는 곳마다 같은 트랜잭션에서 대응 ItemSnapshot 도 평행하게 처리한다.
@Service
class TournamentItemPersistenceService(
    private val tournamentRepository: TournamentRepository,
    private val tournamentUserRepository: TournamentUserRepository,
    private val tournamentItemRepository: TournamentItemRepository,
    private val itemRepository: ItemRepository,
    private val itemSnapshotRepository: ItemSnapshotRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun persistLinkItem(
        userId: UUID,
        tournamentId: Long,
        link: ProductLink,
    ): PersistedTournamentItem {
        validateAndCheckCapacity(userId, tournamentId, 1)
        val item = itemRepository.save(Item.processing(link))
        // 3단계: 저장한 snapshot 의 id 를 tournament_item 에 고정한다. 출전 시점 버전이 박혀 위시 갱신과 격리된다.
        val snapshot = itemSnapshotRepository.save(ItemSnapshot.forItem(item))
        val tournamentItem = tournamentItemRepository.saveAll(
            listOf(
                TournamentItem(
                    tournamentId = tournamentId,
                    itemId = item.getId(),
                    userId = userId,
                    snapshotId = snapshot.getId(),
                ),
            ),
        ).first()
        // 링크 1개 추가. 트랜잭션 안에서 발행 → AFTER_COMMIT 후 알림. 수신자는 참가자 - 본인(actor).
        eventPublisher.publishEvent(TournamentItemAdded(tournamentId = tournamentId, actorId = userId))
        return PersistedTournamentItem(itemId = item.getId(), tournamentItemId = tournamentItem.getId())
    }

    @Transactional
    fun persistProcessingItems(
        userId: UUID,
        tournamentId: Long,
        count: Int,
    ): List<PersistedTournamentItem> {
        validateAndCheckCapacity(userId, tournamentId, count)
        val items = itemRepository.saveAll(List(count) { Item(status = ItemStatus.PROCESSING) })
        // saveAll 은 입력 순서를 보존하므로 items[i] 와 snapshots[i] 가 같은 상품이다. 각 tournament_item 에 그 snapshot 을 고정한다.
        val snapshots = itemSnapshotRepository.saveAll(items.map { ItemSnapshot.forItem(it) })
        val tournamentItems = tournamentItemRepository.saveAll(
            items.zip(snapshots) { item, snapshot ->
                TournamentItem(
                    tournamentId = tournamentId,
                    itemId = item.getId(),
                    userId = userId,
                    snapshotId = snapshot.getId(),
                )
            },
        )
        // 이미지 N개를 한 번에 올려도 "추가됨" 알림은 1회. 수신자는 참가자 - 본인(actor).
        eventPublisher.publishEvent(TournamentItemAdded(tournamentId = tournamentId, actorId = userId))
        return items.zip(tournamentItems) { item, tournamentItem ->
            PersistedTournamentItem(itemId = item.getId(), tournamentItemId = tournamentItem.getId())
        }
    }

    // FAILED item 의 수동 보정 영속화 — S3 업로드(외부 호출)는 호출부가 트랜잭션 바깥에서 끝내고,
    // 여기선 권한·상태 검증 + recover(값 변경 + FAILED→READY 전이)만 짧은 트랜잭션으로 묶는다(dirty checking).
    // recover 게이트를 통과한 뒤 같은 item 의 snapshot 도 평행하게 보정한다(전환기엔 snapshot 이 없을 수 있어 null-safe).
    @Transactional
    fun recoverItem(
        userId: UUID,
        tournamentId: Long,
        tournamentItemId: Long,
        name: String?,
        price: Int?,
        imageUrl: String?,
        currency: String?,
    ) {
        val tournament =
            tournamentRepository.findTournamentById(tournamentId)
                ?: throw TournamentException.notFoundTournament()
        if (!tournament.isPending()) throw TournamentException.notPendingTournament()
        tournamentUserRepository.findByTournamentIdAndUserId(tournamentId, userId)
            ?: throw TournamentException.forbiddenTournament()
        val tournamentItem =
            tournamentItemRepository.findById(tournamentItemId)
                ?: throw TournamentException.notFoundTournamentItem()
        if (tournamentItem.tournamentId != tournamentId) throw TournamentException.notFoundTournamentItem()
        if (tournamentItem.userId != userId) throw TournamentException.forbiddenTournament()
        val item =
            itemRepository.findById(tournamentItem.itemId)
                ?: error("item 없음 — tournamentItemId=$tournamentItemId, itemId=${tournamentItem.itemId}")
        item.recover(name = name, currentPrice = price, imageUrl = imageUrl, currency = currency)
        // 토너먼트는 출전 시점 고정 snapshot 을 본다. 최신(findLatestByItemId)이 아니라 tournamentItem.snapshotId 를
        // 갱신해야, 5단계 갱신으로 같은 item 에 snapshot 이 여러 개 생겨도 토너먼트가 고정한 버전만 보정돼 격리가 유지된다.
        val snapshotId =
            tournamentItem.snapshotId
                ?: error("snapshot 없음 — tournamentItemId=$tournamentItemId, itemId=${tournamentItem.itemId}")
        val snapshot =
            itemSnapshotRepository.findById(snapshotId)
                ?: error("snapshot 없음 — tournamentItemId=$tournamentItemId, snapshotId=$snapshotId")
        snapshot.recover(name = name, currentPrice = price, imageUrl = imageUrl, currency = currency)
    }

    private fun validateAndCheckCapacity(
        userId: UUID,
        tournamentId: Long,
        incomingCount: Int,
    ) {
        val tournament =
            tournamentRepository.findTournamentByIdForUpdate(tournamentId)
                ?: throw TournamentException.notFoundTournament()
        if (!tournament.isPending()) throw TournamentException.notPendingTournament()
        tournamentUserRepository.findByTournamentIdAndUserId(tournamentId, userId)
            ?: throw TournamentException.forbiddenTournament()
        val existingCount = tournamentItemRepository.countByTournamentId(tournamentId)
        if (existingCount + incomingCount >
            TOURNAMENT_MAX_ITEM_COUNT
        ) {
            throw TournamentException.tooManyTournamentItems()
        }
    }
}
