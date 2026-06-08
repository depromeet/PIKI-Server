package com.depromeet.piki.wishlist.service

import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.item.domain.ItemSnapshot
import com.depromeet.piki.item.repository.ItemRepository
import com.depromeet.piki.item.repository.ItemSnapshotRepository
import com.depromeet.piki.wishlist.domain.Wish
import com.depromeet.piki.wishlist.repository.WishRepository
import com.depromeet.piki.wishlist.service.dto.WishWithItem
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// WishlistService 의 registerFromUrl 가 외부 LLM 호출을 트랜잭션 바깥에 두도록
// 영속화만 별도 빈으로 분리. 같은 빈에서 호출하면 Spring AOP proxy 를
// 거치지 않아 @Transactional 가 무력화되기 때문이다.
//
// item 은 정체성(link)만 들고 추출값·상태는 ItemSnapshot 이 보유한다. URL 등록 경로는 link 만 가진 item 과
// PENDING snapshot(outbox 적재)을 같은 트랜잭션에서 함께 저장하고, wish 가 그 snapshot 을 활성 포인터로 가리킨다.
// 파싱은 디스패처(@Scheduled)가 PENDING 을 집어 시작하므로, 여기선 워커를 트리거하지 않는다.
@Service
class WishPersistenceService(
    private val wishRepository: WishRepository,
    private val itemRepository: ItemRepository,
    private val itemSnapshotRepository: ItemSnapshotRepository,
) {
    // item(정체성) → snapshot(PENDING 버전) → wish 순서로 같은 트랜잭션에서 저장한다.
    // item 생성은 호출부가 트랜잭션 바깥에서 끝내고, 여기선 영속화만 한다.
    // snapshot 을 PENDING 으로 커밋하는 것이 곧 outbox 적재다 — 디스패처가 이 행을 집어 PROCESSING 으로 claim 한다.
    @Transactional
    fun persist(
        userId: UUID,
        item: Item,
    ): WishWithItem {
        val saved = itemRepository.save(item)
        // 저장한 snapshot 의 id 를 wish 의 활성 포인터(snapshotId)로 박는다. 5단계 갱신에서 새 버전으로 스왑된다.
        val snapshot = itemSnapshotRepository.save(ItemSnapshot.pending(saved.getId()))
        val wish = wishRepository.save(Wish(userId = userId, itemId = saved.getId(), snapshotId = snapshot.getId()))
        return WishWithItem(wish = wish, item = saved, snapshot = snapshot)
    }

    // 이미지 다건 등록용 — link 없는 item 을 count 만큼 배치 저장하고, 각각에 PROCESSING snapshot·wish 를 건다.
    // 추출은 호출부가 비동기 워커에 위임하므로 여기선 PROCESSING 버전만 영속화한다.
    @Transactional
    fun persistProcessingImages(
        userId: UUID,
        count: Int,
    ): List<WishWithItem> {
        val items = itemRepository.saveAll(List(count) { Item() })
        // snapshot 을 itemId 로 매핑해 saveAll 반환 순서에 의존하지 않는다(순서 보존은 공식 계약이 아니다).
        val snapshotsByItemId =
            itemSnapshotRepository.saveAll(items.map { ItemSnapshot.processing(it.getId()) }).associateBy { it.itemId }
        return items.map { item ->
            val snapshot = snapshotsByItemId[item.getId()] ?: error("item ${item.getId()} 의 snapshot 이 없다")
            val wish = wishRepository.save(Wish(userId = userId, itemId = item.getId(), snapshotId = snapshot.getId()))
            WishWithItem(wish = wish, item = item, snapshot = snapshot)
        }
    }

    // FAILED 버전의 수동 보정 영속화 — S3 업로드(외부 호출)는 호출부가 트랜잭션 바깥에서 끝내고,
    // 여기선 snapshot.recover(값 변경 + FAILED→READY 전이)만 짧은 트랜잭션으로 묶는다(dirty checking).
    // recover 가 READY/PROCESSING 을 409, 이름 없음을 400 으로 막는다(도메인 자기방어). item 은 정체성이라 건드리지 않는다.
    @Transactional
    fun recoverItem(
        itemId: Long,
        name: String?,
        currentPrice: Int?,
        imageUrl: String?,
        currency: String?,
    ): Item {
        val item = itemRepository.findById(itemId) ?: error("item $itemId 가 없다")
        val snapshot =
            itemSnapshotRepository.findLatestByItemId(itemId)
                ?: error("item $itemId 의 snapshot 이 없다")
        snapshot.recover(name = name, currentPrice = currentPrice, imageUrl = imageUrl, currency = currency)
        return item
    }
}
