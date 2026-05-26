package com.depromeet.piki.tournament.service

import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.item.domain.ItemStatus
import com.depromeet.piki.item.repository.ItemRepository
import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.tournament.domain.TournamentItem
import com.depromeet.piki.tournament.repository.TournamentItemRepository
import com.depromeet.piki.tournament.repository.TournamentRepository
import com.depromeet.piki.tournament.repository.TournamentUserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// TournamentItemService 의 외부 호출(링크 추출·OCR)이 트랜잭션 밖에 있도록
// 아이템 저장과 토너먼트 아이템 등록만 별도 빈으로 분리한다.
// 같은 빈에서 @Transactional 메서드를 직접 호출하면 Spring AOP proxy 를 거치지 않아 트랜잭션이 무력화된다.
@Service
class TournamentItemPersistenceService(
    private val tournamentRepository: TournamentRepository,
    private val tournamentUserRepository: TournamentUserRepository,
    private val tournamentItemRepository: TournamentItemRepository,
    private val itemRepository: ItemRepository,
) {
    @Transactional
    fun persistItems(
        userId: UUID,
        tournamentId: Long,
        items: List<Item>,
    ) {
        validateAndCheckCapacity(userId, tournamentId, items.size)
        val savedItems = itemRepository.saveAll(items)
        tournamentItemRepository.saveAll(
            savedItems.map { TournamentItem(tournamentId = tournamentId, itemId = it.getId(), userId = userId) },
        )
    }

    @Transactional
    fun persistLinkItem(
        userId: UUID,
        tournamentId: Long,
        link: ProductLink,
    ): Long {
        validateAndCheckCapacity(userId, tournamentId, 1)
        val item = itemRepository.save(Item.processing(link))
        tournamentItemRepository.saveAll(listOf(TournamentItem(tournamentId = tournamentId, itemId = item.getId(), userId = userId)))
        return item.getId()
    }

    @Transactional
    fun persistProcessingItems(
        userId: UUID,
        tournamentId: Long,
        count: Int,
    ): List<Long> {
        validateAndCheckCapacity(userId, tournamentId, count)
        val items = itemRepository.saveAll(List(count) { Item(status = ItemStatus.PROCESSING) })
        tournamentItemRepository.saveAll(
            items.map { TournamentItem(tournamentId = tournamentId, itemId = it.getId(), userId = userId) },
        )
        return items.map { it.getId() }
    }

    private fun validateAndCheckCapacity(
        userId: UUID,
        tournamentId: Long,
        incomingCount: Int,
    ) {
        val tournament =
            tournamentRepository.findTournamentById(tournamentId)
                ?: throw TournamentException.notFoundTournament()
        if (!tournament.isPending()) throw TournamentException.notPendingTournament()
        tournamentUserRepository.findByTournamentIdAndUserId(tournamentId, userId)
            ?: throw TournamentException.forbiddenTournament()
        val existing = tournamentItemRepository.findAllByTournamentId(tournamentId)
        if (existing.size + incomingCount > TOURNAMENT_MAX_ITEM_COUNT) throw TournamentException.tooManyTournamentItems()
    }
}
