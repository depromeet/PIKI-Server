package com.depromeet.piki.wishlist.service

import com.depromeet.piki.image.domain.ProductImage
import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.item.repository.ItemRepository
import com.depromeet.piki.item.service.ImageParsingWorker
import com.depromeet.piki.item.service.ItemParsingService
import com.depromeet.piki.item.service.ItemParsingWorker
import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.wishlist.domain.WishCursor
import com.depromeet.piki.wishlist.domain.WishException
import com.depromeet.piki.wishlist.domain.WishlistSize
import com.depromeet.piki.wishlist.repository.WishRepository
import com.depromeet.piki.wishlist.service.dto.WishWithItem
import com.depromeet.piki.wishlist.service.dto.WishlistPage
import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskRejectedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Service
class WishlistService(
    private val itemParsingWorker: ItemParsingWorker,
    private val imageParsingWorker: ImageParsingWorker,
    private val itemParsingService: ItemParsingService,
    private val wishPersistenceService: WishPersistenceService,
    private val wishRepository: WishRepository,
    private val itemRepository: ItemRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // register 는 외부 LLM 호출(read-timeout 60s)을 동기로 기다리지 않는다.
    // link 만 가진 PROCESSING item 을 즉시 저장해 응답을 돌려주고(클라이언트는 "담는 중" 표시),
    // 실제 파싱은 itemParsingWorker 가 백그라운드에서 수행해 READY/FAILED 로 전이시킨다.
    // URL 형식 같은 계약 위반은 ProductLink.parse 가 동기로 거른다(400). 파싱 결과 실패만 FAILED 로 간다.
    fun register(
        rawUrl: String,
        userId: UUID,
    ): WishWithItem {
        val link = ProductLink.parse(rawUrl)
        val result = wishPersistenceService.persist(userId, Item.processing(link))
        // 워커 디스패치가 큐 포화 등으로 거부되면 PROCESSING 으로 방치하지 않고 즉시 FAILED 로 떨어뜨린다.
        runCatching { itemParsingWorker.parse(result.item.getId(), link) }
            .onFailure { e ->
                log.warn("파싱 워커 디스패치 실패, item {} 를 FAILED 처리: {}", result.item.getId(), e.message)
                itemParsingService.markFailed(result.item.getId())
            }
        return result
    }

    // 이미지 등록은 register(link)와 같은 비동기 흐름 — 입력이 이미지(다건)일 뿐이다.
    // 개수·형식을 동기로 검증(400)한 뒤 PROCESSING item·wish 를 배치 저장해 즉시 반환하고,
    // 실제 추출(Gemini·크롭·S3)은 imageParsingWorker 가 백그라운드에서 각 이미지를 병렬 파싱해
    // READY/FAILED 로 전이시킨다. 토너먼트 아이템 이미지 등록과 동일한 패턴이다.
    fun registerFromImages(
        images: List<MultipartFile>,
        userId: UUID,
    ): List<WishWithItem> {
        if (images.size !in MIN_IMAGE_COUNT..MAX_IMAGE_COUNT) throw WishException.invalidImageCount()
        // 형식 검증(빈 바이트·미지원 MIME) — 실패 시 즉시 400. 유효한 이미지만 PROCESSING 으로 등록한다.
        val productImages = images.map { ProductImage.of(it.bytes, it.contentType) }
        val results = wishPersistenceService.persistProcessingImages(userId, productImages.size)
        results.zip(productImages).forEach { (result, productImage) ->
            val itemId = result.item.getId()
            // 워커 디스패치가 큐 포화 등으로 거부되면 PROCESSING 으로 방치하지 않고 즉시 FAILED 로 떨어뜨린다.
            try {
                imageParsingWorker.parse(itemId, productImage)
            } catch (e: TaskRejectedException) {
                log.warn("파싱 워커 디스패치 거부, item {} 를 FAILED 처리: {}", itemId, e.message)
                runCatching { itemParsingService.markFailed(itemId) }
                    .onFailure { ex -> log.error("item {} FAILED 전이 실패, PROCESSING 방치 위험", itemId, ex) }
            }
        }
        return results
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

        val nextCursor =
            pageWishes
                .lastOrNull()
                ?.getId()
                ?.toString()
                .takeIf { hasNext }
        return WishlistPage(entries = entries, nextCursor = nextCursor, hasNext = hasNext)
    }

    // wishId 로 단건 조회. 본인 위시만 볼 수 있고, 권한 검증은 도메인(verifyOwnedBy)에 맡긴다.
    // findById 가 deletedAt IS NULL 만 보므로 삭제된 위시는 notFound(404)로 떨어진다.
    @Transactional(readOnly = true)
    fun getWish(
        userId: UUID,
        wishId: Long,
    ): WishWithItem {
        val wish = wishRepository.findById(wishId) ?: throw WishException.notFound()
        wish.verifyOwnedBy(userId)
        // wish 가 가리키는 item 은 반드시 존재한다. 없으면 영속화 경로가 깨진 코드 버그다.
        val item = itemRepository.findById(wish.itemId) ?: error("wish ${wish.getId()} 의 item ${wish.itemId} 가 없다")
        return WishWithItem(wish = wish, item = item)
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

    // 여러 위시를 한 번에 삭제한다(all-or-nothing). 단건 삭제와 같은 검증을 집합으로 확장한 것이라
    // 존재(404) → 소유(403) 순서·의미가 단건과 일치한다. @Transactional 이라 검증 실패 시 아무것도 지워지지 않는다.
    @Transactional
    fun deleteWishes(
        userId: UUID,
        wishIds: List<Long>,
    ) {
        // 중복 id 는 같은 위시를 가리킬 뿐이므로 정규화한다. 정규화 후 개수가 존재 검증의 기준이 된다.
        val distinctIds = wishIds.distinct()
        val wishes = wishRepository.findAllByIds(distinctIds)
        // 하나라도 없으면(이미 삭제됨 포함) 전체를 404 로 막는다 — 단건의 findById null → notFound 와 같은 계약.
        if (wishes.size != distinctIds.size) throw WishException.notFound()
        // 하나라도 본인 소유가 아니면 전체를 403 으로 막는다. 도메인이 자기방어한다.
        wishes.forEach { it.verifyOwnedBy(userId) }
        wishes.forEach { it.delete() }
    }

    companion object {
        private const val MIN_IMAGE_COUNT = 1
        private const val MAX_IMAGE_COUNT = 5
    }
}
