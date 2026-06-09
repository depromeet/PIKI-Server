package com.depromeet.piki.item.service

import com.depromeet.piki.item.event.ItemParsingCompleted
import com.depromeet.piki.item.event.ItemParsingFailed
import com.depromeet.piki.item.repository.ItemRepository
import com.depromeet.piki.item.repository.ItemSnapshotRepository
import com.depromeet.piki.product.service.ProductSnapshot
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

// 파싱 결과의 상태 전이만 짧은 트랜잭션으로 영속화한다 (전이는 dirty checking 으로 커밋 시 반영).
// 외부 호출(extract)은 워커가 트랜잭션 바깥에서 끝낸다. 워커(@Async)·디스패처(@Scheduled)와 별도 빈으로 두어
// AOP proxy 를 거치게 한다(self-invocation 회피).
//
// 추출값·상태는 ItemSnapshot 이 보유하므로 전이도 snapshot 단독으로 한다 — item(정체성)은 건드리지 않는다.
@Service
class ItemParsingService(
    private val itemRepository: ItemRepository,
    private val itemSnapshotRepository: ItemSnapshotRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun markReady(
        itemId: Long,
        snapshot: ProductSnapshot,
    ) {
        // 워커가 방금 저장한 PROCESSING 버전을 전이시킨다. 없으면 영속화 경로가 깨진 코드 버그다.
        val target =
            itemSnapshotRepository.findLatestByItemId(itemId)
                ?: error("파싱 대상 snapshot (item $itemId) 이 없다")
        target.markReady(snapshot)
        // 트랜잭션 안에서 발행 → AFTER_COMMIT 리스너가 커밋 성공 후에만 알림을 보낸다 (롤백 시 발송 안 됨).
        eventPublisher.publishEvent(ItemParsingCompleted(itemId))
    }

    @Transactional
    fun markFailed(itemId: Long) {
        val target =
            itemSnapshotRepository.findLatestByItemId(itemId)
                ?: error("파싱 대상 snapshot (item $itemId) 이 없다")
        target.markFailed()
        eventPublisher.publishEvent(ItemParsingFailed(itemId))
    }

    // 디스패처가 PENDING 작업을 집어 PROCESSING 으로 claim 한다 (짧은 트랜잭션 + FOR UPDATE 락).
    // 실제 파싱(외부 LLM, 트랜잭션 밖)은 디스패처가 반환받은 ClaimedItem 으로 워커에 넘긴다.
    //
    // batch 전체가 한 트랜잭션이므로 한 행에서 throw 하면 batch 전체가 롤백되고, FIFO 라 같은 선두 batch 가
    // 매 tick 재fetch 돼 poison-pill 로 디스패치가 영구 정지한다. 따라서 이상 행도 throw 없이 처리한다:
    //   - snapshot 은 FOR UPDATE 로 PENDING 으로 잠겨 있어 markProcessing 은 throw 하지 않는다.
    //   - link 없는 PENDING(정상 흐름엔 없음 — URL 등록만 PENDING 이고 항상 link 보유)은 markProcessing 으로
    //     PENDING 큐에서 빼되 워커에 안 넘긴다 → recover 가 stale 로 잡아 FAILED 로 종결한다(영구 PENDING 방지).
    @Transactional
    fun claimDuePending(batchSize: Int): List<ClaimedItem> {
        val snapshots = itemSnapshotRepository.findDuePending(batchSize)
        if (snapshots.isEmpty()) return emptyList()
        // per-snapshot N+1 대신 link 를 item 에서 한 번에 로드한다 (snapshot 은 itemId 만 들고 link 는 item 소관).
        val linkByItemId = itemRepository.findByIds(snapshots.map { it.itemId }).associateBy({ it.getId() }, { it.link })
        return snapshots.mapNotNull { snapshot ->
            snapshot.markProcessing()
            val link =
                linkByItemId[snapshot.itemId] ?: run {
                    log.error(
                        "PENDING snapshot {} (item {}) 에 link 가 없어 claim 제외 (URL 등록 경로만 PENDING 이어야 한다)",
                        snapshot.getId(),
                        snapshot.itemId,
                    )
                    return@mapNotNull null
                }
            ClaimedItem(itemId = snapshot.itemId, link = link)
        }
    }

    // stale PROCESSING(워커 크래시·실행 누락·일시 오류로 단건 실행이 끝나지 않은 행)을 집어 재실행 또는 종결한다.
    // claim-at-least-once 를 execution at-least-once 로 끌어올리는 핵심(#461) — 기존의 "무조건 FAILED" 를 "재실행 우선"으로 바꿨다.
    //
    // 단건 시도는 워커가 60s 안에 끝내므로(외부 timeout 합 ≤ 약 55s, Gemini 내부 재시도 off), updated_at 이 60s 보다 오래된
    // PROCESSING 은 워커가 더는 돌고 있지 않다는 뜻이다. 그런 행을:
    //   - link 가 없으면(이미지 경로 — 원본이 메모리 ByteArray 라 크래시 시 소실) 되살릴 수 없으므로 즉시 FAILED.
    //   - attempt 가 상한(maxAttempts)에 도달했으면 더 시도하지 않고 FAILED (무한 재큐잉 방지, 절대 3분 초과 금지).
    //   - 그 외에는 reclaim(attempt++, PROCESSING 유지)해 재실행 대상으로 반환한다 — 실제 워커 제출은 스케줄러가 트랜잭션 밖에서 한다.
    //
    // snapshot 은 FOR UPDATE 로 PROCESSING 으로 잠겨 reclaim·markFailed 가 throw 하지 않으므로 batch poison 이 없다.
    @Transactional
    fun retryOrFailStaleProcessing(
        threshold: LocalDateTime,
        batchSize: Int,
        maxAttempts: Int,
    ): StaleProcessingOutcome {
        val stale = itemSnapshotRepository.findStaleProcessing(threshold, batchSize)
        if (stale.isEmpty()) return StaleProcessingOutcome(emptyList(), 0)
        // per-snapshot N+1 대신 link 를 item 에서 한 번에 로드한다 (snapshot 은 itemId 만 들고 link 는 item 소관).
        val linkByItemId = itemRepository.findByIds(stale.map { it.itemId }).associateBy({ it.getId() }, { it.link })
        val toRetry = mutableListOf<ClaimedItem>()
        var failedCount = 0
        stale.forEach { snapshot ->
            // link 없음(이미지·orphan): 되살릴 원본이 없으므로 종결. associateBy 값이 null(이미지)이든 키 부재(orphan)든 Elvis 로 동일 처리.
            val link =
                linkByItemId[snapshot.itemId] ?: run {
                    snapshot.markFailed()
                    eventPublisher.publishEvent(ItemParsingFailed(snapshot.itemId))
                    failedCount++
                    return@forEach
                }
            // 재시도 상한 도달: 더 되살리지 않고 종결.
            if (snapshot.attemptCount >= maxAttempts) {
                snapshot.markFailed()
                eventPublisher.publishEvent(ItemParsingFailed(snapshot.itemId))
                failedCount++
                return@forEach
            }
            // 재실행: PROCESSING 유지 + attempt++ (updated_at 갱신으로 stale 시계 리셋). 디스패치는 스케줄러가.
            snapshot.reclaim()
            toRetry.add(ClaimedItem(itemId = snapshot.itemId, link = link))
        }
        return StaleProcessingOutcome(toRetry, failedCount)
    }
}
