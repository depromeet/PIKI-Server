package com.depromeet.piki.item.service

import com.depromeet.piki.item.repository.ItemRepository
import com.depromeet.piki.product.service.ProductSnapshot
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 파싱 결과의 상태 전이만 짧은 트랜잭션으로 영속화한다 (전이는 dirty checking 으로 커밋 시 반영).
// 외부 호출(extract)은 워커가 트랜잭션 바깥에서 끝낸다. 워커(@Async)와 별도 빈으로 두어
// AOP proxy 를 거치게 한다(self-invocation 회피).
@Service
class ItemParsingService(
    private val itemRepository: ItemRepository,
) {
    @Transactional
    fun markReady(
        itemId: Long,
        snapshot: ProductSnapshot,
    ) {
        // 워커가 방금 저장한 PROCESSING item 을 전이시킨다. 없으면 영속화 경로가 깨진 코드 버그다.
        val item = itemRepository.findById(itemId) ?: error("파싱 대상 item $itemId 가 없다")
        item.markReady(snapshot)
    }

    @Transactional
    fun markFailed(itemId: Long) {
        val item = itemRepository.findById(itemId) ?: error("파싱 대상 item $itemId 가 없다")
        item.markFailed()
    }
}
