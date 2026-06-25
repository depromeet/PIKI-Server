package com.depromeet.piki.item.service

import com.depromeet.piki.common.config.AsyncConfig
import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.exception.HttpMappable
import com.depromeet.piki.common.storage.ImageStorage
import com.depromeet.piki.image.domain.ProductImage
import com.depromeet.piki.image.service.ImageCropper
import com.depromeet.piki.image.service.ProductImageExtractor
import com.depromeet.piki.product.service.ProductSnapshot
import com.depromeet.piki.product.service.ProductSnapshotException
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.util.UUID

// itemParsingExecutor 스레드에서 이미지 파싱 한 건을 수행한다. 입력은 등록 시 S3 에 durable 적재한 raw object key —
// 워커가 그 key 로 원본을 다시 읽어(download) 파싱하므로, 메모리 ByteArray 와 달리 재실행 시점에 원본이 살아 있다.
// 외부 호출(download·extract·crop·upload)은 트랜잭션 바깥에서 끝내고, 상태 전이 영속화만 ItemParsingService(@Transactional)에 위임한다.
//
// 결과는 셋으로 갈린다(AsyncItemParsingWorker 와 동일한 execution at-least-once 정책, #461):
//   - 성공 → READY. 파싱이 끝났으니 raw 원본을 회수(delete)한다.
//   - 확정 실패(상품 아님·추출값 신뢰 불가·READY 전이 거부) → 즉시 FAILED + raw 회수. 다시 해도 결과가 같다.
//   - 일시 외부 오류(S3·Gemini timeout·5xx 등 RETRYABLE) → 아무것도 안 하고 PROCESSING 유지. raw 는 보존하고 recover 가 상한까지 재실행한다.
@Component
class AsyncImageParsingWorker(
    private val productImageExtractor: ProductImageExtractor,
    private val imageCropper: ImageCropper,
    private val imageStorage: ImageStorage,
    private val itemParsingService: ItemParsingService,
    private val meterRegistry: MeterRegistry,
) : ImageParsingWorker {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async(AsyncConfig.ITEM_PARSING_EXECUTOR)
    override fun parse(
        itemId: Long,
        snapshotId: Long,
        imageKey: String,
    ) {
        runCatching {
            val stored = imageStorage.download(imageKey)
            val image = ProductImage.of(stored.bytes, stored.contentType)
            val extraction = productImageExtractor.extract(image)
            // bbox 있으면 크롭 이미지를, 없으면 원본을 결과 이미지로 S3 에 올린다(READY 불변식: imageUrl 필수).
            val bytes = extraction.boundingBox?.let { imageCropper.crop(image.bytes, it) } ?: image.bytes
            val imageUrl = imageStorage.upload(bytes, "items/${UUID.randomUUID()}.png", "image/png")
            extraction.snapshot.copy(imageUrl = imageUrl)
        }.onSuccess { snapshot -> onExtracted(itemId, snapshotId, imageKey, snapshot) }
            .onFailure { e -> onExtractFailed(itemId, snapshotId, imageKey, e) }
    }

    private fun onExtracted(
        itemId: Long,
        snapshotId: Long,
        imageKey: String,
        snapshot: ProductSnapshot,
    ) {
        runCatching { itemParsingService.markReady(snapshotId, snapshot) }
            .onSuccess {
                log.info("item {} 이미지 파싱 완료 → READY", itemId)
                ItemParsingMetrics.record(meterRegistry, ItemParsingMetrics.RESULT_READY, ItemParsingMetrics.REASON_NONE)
                deleteRawQuietly(imageKey)
            }
            .onFailure { e ->
                // 추출은 됐으나 값을 신뢰할 수 없어 READY 로 채울 수 없음 → PROCESSING 방치 대신 FAILED.
                log.warn("item {} READY 전이 거부 → FAILED: {}", itemId, e.message)
                markFailedQuietly(itemId, snapshotId)
                ItemParsingMetrics.record(meterRegistry, ItemParsingMetrics.RESULT_FAILED, ItemParsingMetrics.REASON_READY_REJECTED)
                deleteRawQuietly(imageKey)
            }
    }

    // 파싱 실패는 두 갈래다 — 일시 오류는 recover 에 맡기고(PROCESSING 유지), 확정 실패만 즉시 종결한다.
    // 판정은 ErrorCategory 가 쥔다(AsyncItemParsingWorker 와 동일): RETRYABLE(S3 다운로드·Gemini timeout·5xx 등)만 PROCESSING 으로 두고,
    // 그 외는 즉시 FAILED. HttpMappable 이 아닌 예상 못한 예외는 일시·영구를 단정할 수 없어 보수적으로 일시로 둔다.
    private fun onExtractFailed(
        itemId: Long,
        snapshotId: Long,
        imageKey: String,
        e: Throwable,
    ) {
        if (isRetryable(e)) {
            // 일시 외부 오류 — FAILED 로 종결하지 않고 PROCESSING 그대로 둔다. raw 는 보존하고 recover 가 stale 로 잡아
            // 상한까지 재실행한다(execution at-least-once, #461). 종결이 아니라 메트릭은 여기서 세지 않는다(recover 가 종결 시 집계).
            val mappable = e as? HttpMappable
            log.warn(
                "item.parse.retry item={} type=image errorType={} category={} status={}",
                itemId,
                e::class.simpleName,
                mappable?.category,
                mappable?.httpStatus?.value(),
            )
            return
        }
        // 확정 실패 — 상품 아님·추출값 신뢰 불가 등. 다시 해도 결과가 같으니 즉시 FAILED + raw 회수.
        val reason = reasonOf(e)
        log.info("item.parse.result item={} type=image result={} reason={}", itemId, ItemParsingMetrics.RESULT_FAILED, reason)
        log.info("item.parse.error item={} reason={} cause={}", itemId, reason, e.message)
        markFailedQuietly(itemId, snapshotId)
        ItemParsingMetrics.record(meterRegistry, ItemParsingMetrics.RESULT_FAILED, reason)
        deleteRawQuietly(imageKey)
    }

    private fun isRetryable(e: Throwable): Boolean = e is HttpMappable && e.category == ErrorCategory.RETRYABLE

    private fun reasonOf(e: Throwable): String =
        when (e) {
            is ProductSnapshotException -> ItemParsingMetrics.REASON_NOT_PRODUCT
            else -> ItemParsingMetrics.REASON_PERMANENT_ERROR
        }

    // raw 원본 회수는 best-effort — 삭제 실패가 파싱 결과(이미 READY/FAILED 확정)를 되돌리지 않는다.
    // 회수 못 한 raw 와 recover 상한 FAILED·유실분은 items/raw/ S3 lifecycle 이 백업으로 만료한다.
    private fun deleteRawQuietly(imageKey: String) {
        runCatching { imageStorage.delete(imageKey) }
            .onFailure { e -> log.warn("raw 이미지 {} 회수 실패(lifecycle 이 만료): {}", imageKey, e.message) }
    }

    private fun markFailedQuietly(
        itemId: Long,
        snapshotId: Long,
    ) {
        runCatching { itemParsingService.markFailed(snapshotId) }
            .onFailure { e ->
                when (e) {
                    is IllegalStateException -> log.info("item {} 는 이미 전이됨, FAILED 처리 생략: {}", itemId, e.message)
                    else -> log.error("item {} FAILED 전이 실패, PROCESSING 방치 위험", itemId, e)
                }
            }
    }
}
