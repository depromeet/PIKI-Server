package com.depromeet.piki.item.service

import com.depromeet.piki.item.event.ItemParsingCompleted
import com.depromeet.piki.item.event.ItemParsingFailed
import com.depromeet.piki.item.repository.ItemSnapshotRepository
import com.depromeet.piki.product.service.ProductSnapshot
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 파싱 결과의 상태 전이만 짧은 트랜잭션으로 영속화한다 (전이는 dirty checking 으로 커밋 시 반영).
// 외부 호출(extract)은 워커가 트랜잭션 바깥에서 끝낸다. 워커(@Async)와 별도 빈으로 두어
// AOP proxy 를 거치게 한다(self-invocation 회피).
//
// 추출값·상태는 ItemSnapshot 이 보유하므로 전이도 snapshot 단독으로 한다 — item(정체성)은 건드리지 않는다.
@Service
class ItemParsingService(
    private val itemSnapshotRepository: ItemSnapshotRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
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
}
