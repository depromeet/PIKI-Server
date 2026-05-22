package com.depromeet.team3.wishlist.service

import com.depromeet.team3.item.domain.Item
import com.depromeet.team3.item.repository.ItemRepository
import com.depromeet.team3.ocr.domain.OcrImage
import com.depromeet.team3.ocr.service.ProductImageExtractor
import com.depromeet.team3.product.domain.ProductLink
import com.depromeet.team3.product.service.ProductLinkExtractor
import com.depromeet.team3.product.service.ProductSnapshot
import com.depromeet.team3.wishlist.domain.WishCursor
import com.depromeet.team3.wishlist.domain.WishException
import com.depromeet.team3.wishlist.domain.WishlistSize
import com.depromeet.team3.wishlist.repository.WishRepository
import com.depromeet.team3.wishlist.service.dto.WishWithItem
import com.depromeet.team3.wishlist.service.dto.WishlistPage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Service
class WishlistService(
    private val productLinkExtractor: ProductLinkExtractor,
    private val productImageExtractor: ProductImageExtractor,
    private val wishPersistenceService: WishPersistenceService,
    private val wishRepository: WishRepository,
    private val itemRepository: ItemRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // register 전체를 @Transactional 로 묶으면 외부 fetch + Gemini 호출 (read-timeout 60s)
    // 동안 DB 커넥션이 잡혀 풀 고갈 → 다른 API 까지 latency 폭증으로 번진다.
    // 외부 호출은 트랜잭션 바깥에서 끝내고, 영속화는 별도 빈에 위임해 proxy 를 통해 호출.
    fun register(
        rawUrl: String,
        userId: UUID,
    ): WishWithItem {
        val link = ProductLink.parse(rawUrl)
        val snapshot = extractWithLatencyLog(link)
        return wishPersistenceService.persist(userId, Item.from(snapshot))
    }

    // OCR 등록도 link 와 같은 흐름 — 입력이 이미지일 뿐. 외부 추출은 트랜잭션 바깥, 영속화만 위임.
    // 추출 결과는 link 추출과 동일한 ProductSnapshot(link=null) 이라 Item.from 을 공유한다.
    fun registerFromOcr(
        image: MultipartFile,
        userId: UUID,
    ): WishWithItem {
        val snapshot = productImageExtractor.extract(OcrImage.of(image.bytes, image.contentType))
        return wishPersistenceService.persist(userId, Item.from(snapshot))
    }

    @Transactional(readOnly = true)
    fun getWishlist(
        userId: UUID,
        rawCursor: String?,
        rawSize: Int?,
    ): WishlistPage {
        val cursor = WishCursor.parse(rawCursor)
        val size = WishlistSize.of(rawSize).value
        // hasNext 판단을 위해 한 건 더 조회하고, 초과분은 응답에서 잘라낸다.
        val fetched = wishRepository.findPage(userId, cursor, size + 1)
        val hasNext = fetched.size > size
        val pageWishes = fetched.take(size)

        val itemsById = itemRepository.findByIds(pageWishes.map { it.itemId }).associateBy { it.getId() }
        val entries =
            pageWishes.map { wish ->
                // item 은 wish 와 함께 영속화되며 별도 삭제 경로가 없다. 없으면 영속화 경로가 깨진 코드 버그다.
                val item = itemsById[wish.itemId] ?: error("wish ${wish.getId()} 의 item ${wish.itemId} 가 없다")
                WishWithItem(wish = wish, item = item)
            }

        val nextCursor = pageWishes.lastOrNull()?.getId()?.toString().takeIf { hasNext }
        return WishlistPage(entries = entries, nextCursor = nextCursor, hasNext = hasNext)
    }

    // item 의 name·currentPrice 를 수정한다. 변경은 @Transactional 커밋 시 dirty checking 으로 반영.
    @Transactional
    fun updateWish(
        userId: UUID,
        wishId: Long,
        name: String?,
        currentPrice: Int?,
        imageUrl: String?,
        currency: String?,
    ): WishWithItem {
        val wish = wishRepository.findById(wishId) ?: throw WishException.notFound()
        wish.verifyOwnedBy(userId)
        // wish 가 가리키는 item 은 반드시 존재한다. 없으면 영속화 경로가 깨진 코드 버그다.
        val item = itemRepository.findById(wish.itemId) ?: error("wish ${wish.getId()} 의 item ${wish.itemId} 가 없다")
        item.update(name = name, currentPrice = currentPrice, imageUrl = imageUrl, currency = currency)
        return WishWithItem(wish = wish, item = item)
    }

    @Transactional
    fun deleteWish(
        userId: UUID,
        wishId: Long,
    ) {
        val wish = wishRepository.findById(wishId) ?: throw WishException.notFound()
        wish.verifyOwnedBy(userId)
        wish.delete()
    }

    private fun extractWithLatencyLog(link: ProductLink): ProductSnapshot {
        val started = System.nanoTime()
        val snapshot = productLinkExtractor.extract(link)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        log.info("extract latency: total={}ms url={}", elapsedMs, link.safeLogString())
        return snapshot
    }
}
