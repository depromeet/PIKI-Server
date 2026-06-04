package com.depromeet.piki.item.service

import com.depromeet.piki.item.event.ItemParsingCompleted
import com.depromeet.piki.item.event.ItemParsingFailed
import com.depromeet.piki.item.repository.ItemRepository
import com.depromeet.piki.item.repository.ItemSnapshotRepository
import com.depromeet.piki.product.service.ProductSnapshot
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

// 파싱 결과의 상태 전이만 짧은 트랜잭션으로 영속화한다 (전이는 dirty checking 으로 커밋 시 반영).
// 외부 호출(extract)은 워커가 트랜잭션 바깥에서 끝낸다. 워커(@Async)와 별도 빈으로 두어
// AOP proxy 를 거치게 한다(self-invocation 회피).
@Service
class ItemParsingService(
    private val itemRepository: ItemRepository,
    private val itemSnapshotRepository: ItemSnapshotRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun markReady(
        itemId: Long,
        snapshot: ProductSnapshot,
    ) {
        // 워커가 방금 저장한 PROCESSING item 을 전이시킨다. 없으면 영속화 경로가 깨진 코드 버그다.
        val item = itemRepository.findById(itemId) ?: error("파싱 대상 item $itemId 가 없다")
        item.markReady(snapshot)
        // 2단계 쓰기 이중화: 같은 item 의 PROCESSING snapshot 도 평행하게 READY 로 전이한다.
        // extractedAt 은 여기(서비스)서 now() 로 주입 — 도메인은 시간을 만들지 않는다.
        // 전환기(백필 전 등록된 item)엔 snapshot 이 아직 없을 수 있어 null-safe 로 둔다.
        itemSnapshotRepository.findLatestByItemId(itemId)?.markReady(snapshot, LocalDateTime.now())
        // 트랜잭션 안에서 발행 → AFTER_COMMIT 리스너가 커밋 성공 후에만 알림을 보낸다 (롤백 시 발송 안 됨).
        eventPublisher.publishEvent(ItemParsingCompleted(itemId))
    }

    @Transactional
    fun markFailed(itemId: Long) {
        val item = itemRepository.findById(itemId) ?: error("파싱 대상 item $itemId 가 없다")
        item.markFailed()
        itemSnapshotRepository.findLatestByItemId(itemId)?.markFailed()
        eventPublisher.publishEvent(ItemParsingFailed(itemId))
    }
}
