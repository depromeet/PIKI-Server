package com.depromeet.piki.wishlist.service

import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.item.domain.ItemStatus
import com.depromeet.piki.item.repository.ItemRepository
import com.depromeet.piki.wishlist.domain.Wish
import com.depromeet.piki.wishlist.repository.WishRepository
import com.depromeet.piki.wishlist.service.dto.WishWithItem
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// WishlistService 의 register 가 외부 LLM 호출을 트랜잭션 바깥에 두도록
// 영속화만 별도 빈으로 분리. 같은 빈에서 호출하면 Spring AOP proxy 를
// 거치지 않아 @Transactional 가 무력화되기 때문이다.
@Service
class WishPersistenceService(
    private val wishRepository: WishRepository,
    private val itemRepository: ItemRepository,
) {
    // item → wish 순서로 같은 트랜잭션에서 저장한다.
    // item 생성(추출 결과 매핑)은 호출부가 트랜잭션 바깥에서 끝내고, 여기선 영속화만 한다.
    @Transactional
    fun persist(
        userId: UUID,
        item: Item,
    ): WishWithItem {
        val saved = itemRepository.save(item)
        val wish = wishRepository.save(Wish(userId = userId, itemId = saved.getId()))
        return WishWithItem(wish = wish, item = saved)
    }

    // 이미지 다건 등록용 — link 없는 PROCESSING item 을 count 만큼 배치 저장하고, 각각에 wish 를 건다.
    // 추출은 호출부가 비동기 워커에 위임하므로 여기선 PROCESSING 상태만 영속화한다.
    @Transactional
    fun persistProcessingImages(
        userId: UUID,
        count: Int,
    ): List<WishWithItem> {
        val items = itemRepository.saveAll(List(count) { Item(status = ItemStatus.PROCESSING) })
        return items.map { item ->
            val wish = wishRepository.save(Wish(userId = userId, itemId = item.getId()))
            WishWithItem(wish = wish, item = item)
        }
    }
}
