package com.depromeet.piki.item.service

import com.depromeet.piki.common.config.AsyncConfig
import com.depromeet.piki.common.storage.ImageStorage
import com.depromeet.piki.image.domain.ProductImage
import com.depromeet.piki.image.service.ImageCropper
import com.depromeet.piki.image.service.ProductImageExtractor
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.util.UUID

// itemParsingExecutor 스레드에서 이미지 파싱을 수행한다.
// 외부 호출(extract, crop, upload)은 트랜잭션 바깥에서 끝내고,
// 상태 전이 영속화만 ItemParsingService(@Transactional) 에 위임해 짧은 트랜잭션으로 묶는다.
// AsyncItemParsingWorker(URL 기반) 와 동일한 패턴 — 실패는 모두 FAILED 전이로 흡수한다.
@Component
class AsyncImageParsingWorker(
    private val productImageExtractor: ProductImageExtractor,
    private val imageCropper: ImageCropper,
    private val imageStorage: ImageStorage,
    private val itemParsingService: ItemParsingService,
) : ImageParsingWorker {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async(AsyncConfig.ITEM_PARSING_EXECUTOR)
    override fun parse(
        itemId: Long,
        image: ProductImage,
    ) {
        runCatching {
            val extraction = productImageExtractor.extract(image)
            // bbox 있으면 크롭 이미지를, 없으면 원본 이미지를 S3에 올린다.
            // 어떤 경우든 이미지 파일로 등록한 이상 imageUrl 이 채워져야 한다(READY 불변식).
            val bytes =
                extraction.boundingBox
                    ?.let { imageCropper.crop(image.bytes, it) }
                    ?: image.bytes
            val imageUrl = imageStorage.upload(bytes, "items/${UUID.randomUUID()}.png", "image/png")
            extraction.snapshot.copy(imageUrl = imageUrl)
        }.onSuccess { snapshot ->
            runCatching { itemParsingService.markReady(itemId, snapshot) }
                .onSuccess { log.info("item {} 이미지 파싱 완료 → READY", itemId) }
                .onFailure { e ->
                    log.warn("item {} READY 전이 실패 → FAILED: {}", itemId, e.message)
                    markFailedQuietly(itemId)
                }
        }.onFailure { e ->
            log.warn("item {} 이미지 파싱 실패: {}", itemId, e.message)
            markFailedQuietly(itemId)
        }
    }

    private fun markFailedQuietly(itemId: Long) {
        runCatching { itemParsingService.markFailed(itemId) }
            .onFailure { e ->
                when (e) {
                    is IllegalStateException -> log.info("item {} 는 이미 전이됨, FAILED 처리 생략: {}", itemId, e.message)
                    else -> log.error("item {} FAILED 전이 실패, PROCESSING 방치 위험", itemId, e)
                }
            }
    }
}
