package com.depromeet.piki.item.service

import com.depromeet.piki.common.config.AsyncConfig
import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.product.service.ProductLinkExtractor
import com.depromeet.piki.product.service.ProductSnapshot
import com.depromeet.piki.product.service.ProductSnapshotException
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

// itemParsingExecutor 스레드에서 "단건 파싱 한 번"을 수행한다. 외부 호출(extract)은 트랜잭션 바깥에서 끝내고,
// 상태 전이 영속화만 ItemParsingService(@Transactional) 에 위임해 짧은 트랜잭션으로 묶는다.
// 결과 처리는 셋으로 갈린다(execution at-least-once, #461): 성공 → READY, 확정 실패(상품 아님·이름 없음) → 즉시 FAILED,
// 일시 외부 오류 → 아무것도 안 하고 PROCESSING 유지(recover 가 상한까지 재실행). 재시도·종결 정책은 워커가 아니라 recover 가 쥔다.
// 전이 호출(markReady/markFailed)은 runCatching 으로 감싸 워커 스레드로 예외가 새지 않게 한다
// (recover 와의 레이스로 이미 전이됐거나, 추출값이 도메인 불변식을 위반하는 경우).
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

    // 파싱 실패는 두 갈래다 — 확정 실패는 즉시 종결, 일시 실패는 recover 에 맡긴다.
    private fun onExtractFailed(
        itemId: Long,
        link: ProductLink,
        e: Throwable,
    ) {
        when (e) {
            // 확정 실패 — 상품 아님·추출값 신뢰 불가. 같은 URL 을 다시 파싱해도 결과가 같으므로 즉시 FAILED 로 종결한다
            // (사용자에게 빨리 알림). 클라이언트 입력 계약 위반이라 서버 입장에선 정상 동작(info).
            is ProductSnapshotException -> {
                log.info("item {} 파싱 실패(확정·재시도 무의미): {} url={}", itemId, e.message, link.safeLogString())
                markFailedQuietly(itemId)
            }
            // 일시 외부 오류(네트워크·timeout·Gemini 5xx 등) — 다시 하면 될 수도 있으므로 FAILED 로 종결하지 않고
            // PROCESSING 그대로 둔다. recover 가 stale 로 잡아 상한까지 재실행한다(execution at-least-once, #461).
            else ->
                log.warn("item {} 파싱 실패(일시 외부 오류) → PROCESSING 유지, recover 가 재실행: url={}", itemId, link.safeLogString(), e)
        }
    }

    // FAILED 전이도 sweeper 와의 레이스로 실패할 수 있어(이미 전이됨) 잡아 흡수한다.
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
