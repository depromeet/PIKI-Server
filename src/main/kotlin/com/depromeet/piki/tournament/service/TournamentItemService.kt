package com.depromeet.piki.tournament.service

import com.depromeet.piki.item.service.ImageParsingWorker
import com.depromeet.piki.item.service.ItemParsingService
import com.depromeet.piki.item.service.ItemParsingWorker
import com.depromeet.piki.ocr.domain.OcrImage
import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.tournament.repository.TournamentRepository
import com.depromeet.piki.tournament.repository.TournamentUserRepository
import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskRejectedException
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Service
class TournamentItemService(
    private val tournamentRepository: TournamentRepository,
    private val tournamentUserRepository: TournamentUserRepository,
    private val itemParsingWorker: ItemParsingWorker,
    private val imageParsingWorker: ImageParsingWorker,
    private val itemParsingService: ItemParsingService,
    private val tournamentItemPersistenceService: TournamentItemPersistenceService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun addItemFromLink(
        userId: UUID,
        tournamentId: Long,
        url: String,
    ): Long {
        val link = ProductLink.parse(url)
        validateTournamentAccess(userId, tournamentId)
        val itemId = tournamentItemPersistenceService.persistLinkItem(userId, tournamentId, link)
        try {
            itemParsingWorker.parse(itemId, link)
        } catch (e: TaskRejectedException) {
            log.warn("item {} async 디스패치 거부 → FAILED 처리", itemId, e)
            runCatching { itemParsingService.markFailed(itemId) }
                .onFailure { ex -> log.error("item {} FAILED 전이 실패, PROCESSING 방치 위험", itemId, ex) }
        }
        return itemId
    }

    fun addItemsFromImages(
        userId: UUID,
        tournamentId: Long,
        images: List<MultipartFile>,
    ): List<Long> {
        if (images.size !in MIN_IMAGE_COUNT..MAX_IMAGE_COUNT) throw TournamentException.invalidImageCount()
        validateTournamentAccess(userId, tournamentId)
        // 형식 검증(빈 바이트·미지원 MIME) — 실패 시 즉시 400. 유효한 이미지만 PROCESSING 으로 등록한다.
        val ocrImages = images.map { OcrImage.of(it.bytes, it.contentType) }
        val itemIds = tournamentItemPersistenceService.persistProcessingItems(userId, tournamentId, ocrImages.size)
        itemIds.zip(ocrImages).forEach { (itemId, ocrImage) ->
            try {
                imageParsingWorker.parse(itemId, ocrImage)
            } catch (e: TaskRejectedException) {
                log.warn("item {} async 디스패치 거부 → FAILED 처리", itemId, e)
                runCatching { itemParsingService.markFailed(itemId) }
                    .onFailure { ex -> log.error("item {} FAILED 전이 실패, PROCESSING 방치 위험", itemId, ex) }
            }
        }
        return itemIds
    }

    private fun validateTournamentAccess(
        userId: UUID,
        tournamentId: Long,
    ) {
        val tournament =
            tournamentRepository.findTournamentById(tournamentId)
                ?: throw TournamentException.notFoundTournament()
        if (!tournament.isPending()) throw TournamentException.notPendingTournament()
        tournamentUserRepository.findByTournamentIdAndUserId(tournamentId, userId)
            ?: throw TournamentException.forbiddenTournament()
    }

    companion object {
        private const val MIN_IMAGE_COUNT = 1
        private const val MAX_IMAGE_COUNT = 5
    }
}
