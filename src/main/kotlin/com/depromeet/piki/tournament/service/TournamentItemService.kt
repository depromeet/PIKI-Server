package com.depromeet.piki.tournament.service

import com.depromeet.piki.common.storage.ImageStorage
import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.ocr.domain.OcrImage
import com.depromeet.piki.ocr.service.ImageCropper
import com.depromeet.piki.ocr.service.ProductImageExtractor
import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.product.service.ProductLinkExtractor
import com.depromeet.piki.product.service.ProductSnapshot
import com.depromeet.piki.tournament.repository.TournamentRepository
import com.depromeet.piki.tournament.service.dto.AddTournamentItemsFromImagesResult
import com.depromeet.piki.tournament.repository.TournamentUserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Service
class TournamentItemService(
    private val tournamentRepository: TournamentRepository,
    private val tournamentUserRepository: TournamentUserRepository,
    private val productLinkExtractor: ProductLinkExtractor,
    private val productImageExtractor: ProductImageExtractor,
    private val imageCropper: ImageCropper,
    private val imageStorage: ImageStorage,
    private val tournamentItemPersistenceService: TournamentItemPersistenceService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun addItemFromLink(
        userId: UUID,
        tournamentId: Long,
        url: String,
    ) {
        validateTournamentAccess(userId, tournamentId)
        val link = ProductLink.parse(url)
        val snapshot = extractWithLatencyLog(link)
        tournamentItemPersistenceService.persistItems(userId, tournamentId, listOf(Item.from(snapshot)))
    }

    fun addItemsFromImages(
        userId: UUID,
        tournamentId: Long,
        images: List<MultipartFile>,
    ): AddTournamentItemsFromImagesResult {
        if (images.size !in MIN_IMAGE_COUNT..MAX_IMAGE_COUNT) throw TournamentException.invalidImageCount()
        validateTournamentAccess(userId, tournamentId)

        val failedIndices = mutableListOf<Int>()
        val successfulItems = mutableListOf<Item>()

        images.forEachIndexed { index, image ->
            runCatching {
                val ocrImage = OcrImage.of(image.bytes, image.contentType)
                val extraction = productImageExtractor.extract(ocrImage)
                val imageUrl =
                    extraction.boundingBox
                        ?.let { imageCropper.crop(ocrImage.bytes, it) }
                        ?.let { cropped ->
                            runCatching {
                                imageStorage.upload(cropped, "items/${UUID.randomUUID()}.png", "image/png")
                            }.getOrElse { e ->
                                log.warn("크롭 이미지 S3 업로드 실패, imageUrl 없이 등록 진행: {}", e.message)
                                null
                            }
                        }
                Item.from(extraction.snapshot.copy(imageUrl = imageUrl))
            }.onSuccess { item ->
                successfulItems.add(item)
            }.onFailure { e ->
                log.warn("이미지[{}] 처리 실패, 해당 이미지 건너뜀: {}", index, e.message)
                failedIndices.add(index)
            }
        }

        if (successfulItems.isNotEmpty()) {
            tournamentItemPersistenceService.persistItems(userId, tournamentId, successfulItems)
        }

        return AddTournamentItemsFromImagesResult(failedIndices = failedIndices)
    }

    private fun extractWithLatencyLog(link: ProductLink): ProductSnapshot {
        val started = System.nanoTime()
        val snapshot = productLinkExtractor.extract(link)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        log.info("extract latency: total={}ms url={}", elapsedMs, link.safeLogString())
        return snapshot
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
