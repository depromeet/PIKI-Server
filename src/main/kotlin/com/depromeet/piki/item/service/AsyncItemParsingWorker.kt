package com.depromeet.piki.item.service

import com.depromeet.piki.common.config.AsyncConfig
import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.product.service.ProductLinkExtractor
import com.depromeet.piki.product.service.ProductSnapshot
import com.depromeet.piki.product.service.ProductSnapshotException
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

// itemParsingExecutor 스레드에서 파싱을 수행한다. 외부 호출(extract)은 트랜잭션 바깥에서 끝내고,
// 상태 전이 영속화만 ItemParsingService(@Transactional) 에 위임해 짧은 트랜잭션으로 묶는다.
// 전이 호출(markReady/markFailed)은 모두 runCatching 으로 감싸 워커 스레드로 예외가 새지 않게 한다
// (sweeper 와의 레이스로 이미 전이됐거나, 추출값이 도메인 불변식을 위반하는 경우).
@Component
class AsyncItemParsingWorker(
    private val productLinkExtractor: ProductLinkExtractor,
    private val itemParsingService: ItemParsingService,
) : ItemParsingWorker {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async(AsyncConfig.ITEM_PARSING_EXECUTOR)
    override fun parse(
        itemId: Long,
        link: ProductLink,
    ) {
        val started = System.nanoTime()
        runCatching { productLinkExtractor.extract(link) }
            .onSuccess { snapshot -> onExtracted(itemId, link, snapshot, started) }
            .onFailure { e -> onExtractFailed(itemId, link, e) }
    }

    private fun onExtracted(
        itemId: Long,
        link: ProductLink,
        snapshot: ProductSnapshot,
        started: Long,
    ) {
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        // 전이가 실패(추출값 도메인 검증 위반·DB 오류·sweeper 와의 레이스로 이미 전이됨)해도 예외를 흡수한다.
        runCatching { itemParsingService.markReady(itemId, snapshot) }
            .onSuccess { log.info("item {} 파싱 완료: latency={}ms url={}", itemId, elapsedMs, link.safeLogString()) }
            .onFailure { e ->
                // 추출은 됐으나 값을 신뢰할 수 없어 READY 로 채울 수 없는 경우 → PROCESSING 방치 대신 FAILED 로.
                log.warn("item {} READY 전이 실패 → FAILED: url={}", itemId, link.safeLogString(), e)
                markFailedQuietly(itemId)
            }
    }

    // 파싱 실패는 동기 400 이 아니라 FAILED 상태로 남긴다. 사유에 따라 로그 레벨만 구분한다.
    private fun onExtractFailed(
        itemId: Long,
        link: ProductLink,
        e: Throwable,
    ) {
        when (e) {
            // 상품 아님·추출값 신뢰 불가 — 클라이언트 입력 계약 위반이라 서버 입장에선 정상 동작(info).
            is ProductSnapshotException ->
                log.info("item {} 파싱 실패(계약): {} url={}", itemId, e.message, link.safeLogString())
            // 외부 호출 실패(네트워크·timeout 등) — warn.
            else ->
                log.warn("item {} 파싱 실패(외부 호출): url={}", itemId, link.safeLogString(), e)
        }
        markFailedQuietly(itemId)
    }

    // FAILED 전이도 sweeper 와의 레이스로 실패할 수 있어(이미 전이됨) 잡아 흡수한다.
    private fun markFailedQuietly(itemId: Long) {
        runCatching { itemParsingService.markFailed(itemId) }
            .onFailure { e -> log.info("item {} 는 이미 전이됨, FAILED 처리 생략: {}", itemId, e.message) }
    }
}
