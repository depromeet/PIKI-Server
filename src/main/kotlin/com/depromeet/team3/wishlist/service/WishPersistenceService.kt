package com.depromeet.team3.wishlist.service

import com.depromeet.team3.item.domain.Item
import com.depromeet.team3.item.repository.ItemRepository
import com.depromeet.team3.product.service.ProductSnapshot
import com.depromeet.team3.wishlist.domain.Wish
import com.depromeet.team3.wishlist.repository.WishRepository
import com.depromeet.team3.wishlist.service.dto.WishWithItem
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
    @Transactional
    fun persist(
        userId: UUID,
        snapshot: ProductSnapshot,
    ): WishWithItem {
        val item = itemRepository.save(Item.from(snapshot))
        val wish = wishRepository.save(Wish(userId = userId, itemId = item.getId()))
        return WishWithItem(wish = wish, item = item)
    }
}
