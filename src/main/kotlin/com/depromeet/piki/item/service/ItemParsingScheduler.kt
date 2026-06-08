package com.depromeet.piki.item.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

// URL 파싱 경로의 outbox 디스패처 + recover.
//
// 등록은 PENDING snapshot 을 커밋만 하고(작업 적재), 작업의 진실 원천은 인메모리 큐가 아니라 DB 의 PENDING 행이다.
// 디스패처가 그 행을 집어 실행하므로, @Async 큐 유실(인스턴스 재시작 등)로 PENDING 이 방치되는 일이 없다 —
// PENDING 은 반드시 한 번은 claim 돼 실행이 시작된다. item_snapshots 테이블 자체가 outbox(상태머신을 가진 작업 큐)라
// 별도 테이블이 없다.
//
// 한계 — 보장은 'claim-at-least-once' 이지 '실행 at-least-once' 가 아니다: claim(PROCESSING 커밋) 직후 워커 제출
// 전에 인스턴스가 죽으면 그 작업은 실행 0회로 PROCESSING 에 갇히고, recover 가 FAILED 로 종결한다(재큐잉 없음 — 사용자가 재등록).
//
// 단일 인스턴스 기준의 @Scheduled 다. 멀티 인스턴스로 가면 claim 의 FOR UPDATE 가 중복 파싱(두 워커가 같은 행)은
// 막지만, SKIP LOCKED 가 없어 두 디스패처가 같은 선두 batch 를 두고 락 대기로 직렬화된다 — 그때는 SKIP LOCKED
// (work-queue 분산) + ShedLock(폴링 단일 실행) 으로 보강해야 한다.
@Component
class ItemParsingScheduler(
    private val itemParsingService: ItemParsingService,
    private val itemParsingWorker: ItemParsingWorker,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 디스패처 — 짧은 주기로 PENDING 을 집어 PROCESSING 으로 claim(짧은 트랜잭션 + FOR UPDATE)한 뒤,
    // 실제 파싱(외부 LLM)은 트랜잭션 밖에서 워커 풀(@Async)에 넘긴다. 등록 후 파싱 시작 지연 = 이 폴링 주기.
    @Scheduled(fixedDelay = DISPATCH_INTERVAL_MS)
    fun dispatch() {
        val claimed = itemParsingService.claimDuePending(BATCH_SIZE)
        if (claimed.isEmpty()) return
        log.info("PENDING {}건 claim → 워커 디스패치", claimed.size)
        claimed.forEach { dispatchToWorker(it) }
    }

    private fun dispatchToWorker(claimed: ClaimedItem) {
        // 워커 풀 포화(queue 초과)로 거부되면 PROCESSING 방치 대신 즉시 FAILED 로 떨군다 (기존 등록 경로와 같은 정책).
        runCatching { itemParsingWorker.parse(claimed.itemId, claimed.link) }
            .onFailure { e ->
                log.warn("item {} 워커 디스패치 거부 → FAILED: {}", claimed.itemId, e.message)
                runCatching { itemParsingService.markFailed(claimed.itemId) }
                    .onFailure { ex -> log.error("item {} FAILED 전이 실패, PROCESSING 방치 위험", claimed.itemId, ex) }
            }
    }

    // recover — 워커가 죽어(인스턴스 크래시 등) PROCESSING 에 갇힌 stale 작업을 주기적으로 FAILED 로 정리한다.
    // 재시도(재큐잉)는 하지 않는다: 사용자와 맞닿은 작업이라 뒤늦은 자동 재실행은 의미가 없고, 사용자가 직접 재등록한다.
    // stale 판정은 updated_at(PROCESSING claim 시각) 기준이라, 정상 처리 중(외부 LLM 60s)인 작업은 걸리지 않는다.
    @Scheduled(fixedDelay = RECOVER_INTERVAL_MS)
    fun recover() {
        val threshold = LocalDateTime.now().minusMinutes(STALE_TIMEOUT_MINUTES)
        val recovered = itemParsingService.recoverStaleProcessing(threshold, BATCH_SIZE)
        if (recovered > 0) log.warn("stale PROCESSING {}건 → FAILED 정리", recovered)
    }

    companion object {
        private const val BATCH_SIZE = 100

        // 사용자 대면 작업이라 짧게 둔다 — 등록 직후 파싱이 시작되기까지의 지연이 이 주기로 결정된다.
        private const val DISPATCH_INTERVAL_MS = 1_000L
        private const val RECOVER_INTERVAL_MS = 60_000L

        // 정상 파싱이 끝났어야 할 시간(외부 LLM read-timeout 60s)을 넉넉히 넘긴 기준. 이보다 오래 PROCESSING 이면 방치로 본다.
        private const val STALE_TIMEOUT_MINUTES = 5L
    }
}
