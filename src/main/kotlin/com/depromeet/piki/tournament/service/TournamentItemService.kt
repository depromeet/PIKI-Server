package com.depromeet.piki.tournament.service

import com.depromeet.piki.common.storage.ImageStorage
import com.depromeet.piki.image.domain.ProductImage
import com.depromeet.piki.item.repository.ItemRepository
import com.depromeet.piki.item.service.ImageParsingWorker
import com.depromeet.piki.item.service.ItemParsingService
import com.depromeet.piki.item.service.ItemParsingWorker
import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.tournament.repository.TournamentItemRepository
import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskRejectedException
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Service
class TournamentItemService(
    private val itemParsingWorker: ItemParsingWorker,
    private val imageParsingWorker: ImageParsingWorker,
    private val itemParsingService: ItemParsingService,
    private val tournamentItemPersistenceService: TournamentItemPersistenceService,
    private val imageStorage: ImageStorage,
    private val tournamentItemRepository: TournamentItemRepository,
    private val itemRepository: ItemRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun addItemFromLink(
        userId: UUID,
        tournamentId: Long,
        url: String,
    ): Long {
        val link = ProductLink.parse(url)
        // 파싱·상태 전이는 item PK 를, 클라이언트 응답은 tournament_item PK 를 쓴다 (PersistedTournamentItem).
        val persisted = tournamentItemPersistenceService.persistLinkItem(userId, tournamentId, link)
        val itemId = persisted.itemId
        try {
            itemParsingWorker.parse(itemId, link)
        } catch (e: TaskRejectedException) {
            log.warn("item {} async 디스패치 거부 → FAILED 처리", itemId, e)
            runCatching { itemParsingService.markFailed(itemId) }
                .onFailure { ex -> log.error("item {} FAILED 전이 실패, PROCESSING 방치 위험", itemId, ex) }
        }
        return persisted.tournamentItemId
    }

    fun addItemsFromImages(
        userId: UUID,
        tournamentId: Long,
        images: List<MultipartFile>,
    ): List<Long> {
        if (images.size !in MIN_IMAGE_COUNT..MAX_IMAGE_COUNT) throw TournamentException.invalidImageCount()
        // 형식 검증(빈 바이트·미지원 MIME) — 실패 시 즉시 400. 유효한 이미지만 PROCESSING 으로 등록한다.
        val productImages = images.map { ProductImage.of(it.bytes, it.contentType) }
        // 파싱·상태 전이는 item PK 를, 클라이언트 응답은 tournament_item PK 를 쓴다 (PersistedTournamentItem).
        val persisted = tournamentItemPersistenceService.persistProcessingItems(userId, tournamentId, productImages.size)
        persisted.zip(productImages).forEach { (persistedItem, productImage) ->
            val itemId = persistedItem.itemId
            try {
                imageParsingWorker.parse(itemId, productImage)
            } catch (e: TaskRejectedException) {
                log.warn("item {} async 디스패치 거부 → FAILED 처리", itemId, e)
                runCatching { itemParsingService.markFailed(itemId) }
                    .onFailure { ex -> log.error("item {} FAILED 전이 실패, PROCESSING 방치 위험", itemId, ex) }
            }
        }
        return persisted.map { it.tournamentItemId }
    }

    // recoverWishItem 과 동일한 패턴 — 이미지 형식 검증 후 S3 업로드는 트랜잭션 밖에서,
    // 권한·상태 검증 + item.recover() 는 recoverItem(@Transactional) 에 위임한다.
    // S3 업로드 전 item 상태를 사전 확인해 orphan 업로드를 방지한다(도메인 최후 보루는 recoverItem).
    fun updateItem(
        userId: UUID,
        tournamentId: Long,
        tournamentItemId: Long,
        name: String?,
        price: Int?,
        currency: String?,
        image: MultipartFile?,
    ) {
        val productImage = image?.let { ProductImage.of(it.bytes, it.contentType) }
        val tournamentItem =
            tournamentItemRepository.findById(tournamentItemId)
                ?: throw TournamentException.notFoundTournamentItem()
        if (tournamentItem.tournamentId != tournamentId) throw TournamentException.notFoundTournamentItem()
        if (tournamentItem.userId != userId) throw TournamentException.forbiddenTournament()
        val item =
            itemRepository.findById(tournamentItem.itemId)
                ?: error("item 없음 — tournamentItemId=$tournamentItemId, itemId=${tournamentItem.itemId}")
        // FAILED 가 아니면 S3 에 올리기 전에 막는다(orphan 업로드 방지). recover 가 사유별 409 를 던진다.
        if (!item.isFailed()) item.recover()
        val imageUrl =
            productImage?.let {
                imageStorage.upload(it.bytes, "tournament-items/${UUID.randomUUID()}.${it.extension}", it.mimeType)
            }
        tournamentItemPersistenceService.recoverItem(userId, tournamentId, tournamentItemId, name, price, imageUrl, currency)
    }

    companion object {
        private const val MIN_IMAGE_COUNT = 1
        private const val MAX_IMAGE_COUNT = 5
    }
}
