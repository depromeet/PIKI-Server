package com.depromeet.piki.wishlist.service

import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.item.domain.ItemSnapshot
import com.depromeet.piki.item.domain.ItemStatus
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
// 2단계 쓰기 이중화: item 을 저장/전이하는 곳마다 같은 트랜잭션에서 대응 ItemSnapshot 도 평행하게 처리한다.
// (등록 경로의 item 은 PROCESSING 이므로 snapshot 도 PROCESSING 으로 시작한다.)
@Service
class WishPersistenceService(
    private val wishRepository: WishRepository,
    private val itemRepository: ItemRepository,
    private val itemSnapshotRepository: ItemSnapshotRepository,
) {
    // item → snapshot → wish 순서로 같은 트랜잭션에서 저장한다.
    // item 생성(추출 결과 매핑)은 호출부가 트랜잭션 바깥에서 끝내고, 여기선 영속화만 한다.
    @Transactional
    fun persist(
        userId: UUID,
        item: Item,
    ): WishWithItem {
        val saved = itemRepository.save(item)
        // 3단계: 저장한 snapshot 의 id 를 wish 의 활성 포인터(snapshotId)로 박는다. 5단계 갱신에서 새 버전으로 스왑된다.
        val snapshot = itemSnapshotRepository.save(ItemSnapshot.forItem(saved))
        val wish = wishRepository.save(Wish(userId = userId, itemId = saved.getId(), snapshotId = snapshot.getId()))
        return WishWithItem(wish = wish, item = saved, snapshot = snapshot)
    }

    // 이미지 다건 등록용 — link 없는 PROCESSING item 을 count 만큼 배치 저장하고, 각각에 snapshot·wish 를 건다.
    // 추출은 호출부가 비동기 워커에 위임하므로 여기선 PROCESSING 상태만 영속화한다.
    @Transactional
    fun persistProcessingImages(
        userId: UUID,
        count: Int,
    ): List<WishWithItem> {
        val items = itemRepository.saveAll(List(count) { Item(status = ItemStatus.PROCESSING) })
        // snapshot 을 itemId 로 매핑해 saveAll 반환 순서에 의존하지 않는다(순서 보존은 공식 계약이 아니다).
        val snapshotsByItemId =
            itemSnapshotRepository.saveAll(items.map { ItemSnapshot.forItem(it) }).associateBy { it.itemId }
        return items.map { item ->
            val snapshot = snapshotsByItemId[item.getId()] ?: error("item ${item.getId()} 의 snapshot 이 없다")
            val wish = wishRepository.save(Wish(userId = userId, itemId = item.getId(), snapshotId = snapshot.getId()))
            WishWithItem(wish = wish, item = item, snapshot = snapshot)
        }
    }

    // FAILED item 의 수동 보정 영속화 — S3 업로드(외부 호출)는 호출부가 트랜잭션 바깥에서 끝내고,
    // 여기선 recover(값 변경 + FAILED→READY 전이)만 짧은 트랜잭션으로 묶는다(dirty checking).
    // recover 가 READY/PROCESSING 을 409, 이름 없음을 400 으로 막는다(도메인 자기방어). 그 게이트를 통과한 뒤
    // 같은 item 의 snapshot 도 평행하게 보정한다(전환기엔 snapshot 이 없을 수 있어 null-safe).
    @Transactional
    fun recoverItem(
        itemId: Long,
        name: String?,
        currentPrice: Int?,
        imageUrl: String?,
        currency: String?,
    ): Item {
        val item = itemRepository.findById(itemId) ?: error("item $itemId 가 없다")
        item.recover(name = name, currentPrice = currentPrice, imageUrl = imageUrl, currency = currency)
        itemSnapshotRepository.findLatestByItemId(itemId)
            ?.recover(name = name, currentPrice = currentPrice, imageUrl = imageUrl, currency = currency)
        return item
    }
}
