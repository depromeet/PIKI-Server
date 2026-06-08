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

    // 워커가 죽어(인스턴스 크래시 등) PROCESSING 에 갇힌 stale 작업을 FAILED 로 정리한다 (재시도/재큐잉 없음 — 사용자가 재등록).
    // snapshot 은 FOR UPDATE 로 PROCESSING 으로 잠겨 markFailed 가 throw 하지 않으므로 batch poison 이 없다.
    // 정리 건수를 반환해 호출부가 요약 로그를 남긴다.
    @Transactional
    fun recoverStaleProcessing(
        threshold: LocalDateTime,
        batchSize: Int,
    ): Int {
        val stale = itemSnapshotRepository.findStaleProcessing(threshold, batchSize)
        if (stale.isEmpty()) return 0
        stale.forEach { snapshot ->
            snapshot.markFailed()
            eventPublisher.publishEvent(ItemParsingFailed(snapshot.itemId))
        }
        return stale.size
    }
}
