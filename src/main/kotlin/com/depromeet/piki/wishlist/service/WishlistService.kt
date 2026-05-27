package com.depromeet.piki.wishlist.service

import com.depromeet.piki.common.storage.ImageStorage
import com.depromeet.piki.image.domain.ProductImage
import com.depromeet.piki.image.service.ImageCropper
import com.depromeet.piki.image.service.ProductImageExtractor
import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.item.repository.ItemRepository
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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Service
class WishlistService(
    private val productImageExtractor: ProductImageExtractor,
    private val imageCropper: ImageCropper,
    private val imageStorage: ImageStorage,
    private val itemParsingWorker: ItemParsingWorker,
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

    // 이미지 등록도 link 와 같은 흐름 — 입력이 이미지일 뿐. 외부 추출·크롭·S3 업로드는 트랜잭션 바깥, 영속화만 위임.
    // 추출 결과는 link 추출과 동일한 ProductSnapshot 이라 Item.from 을 공유한다 (link 만 null).
    // bbox 가 있으면 상품 영역을 크롭해 S3 에 올리고 그 URL 을 imageUrl 로 채운다.
    // bbox 가 없거나(못 잡음) 크롭이 불가한 포맷(HEIC 등)이면 imageUrl 은 null 로 둔다.
    fun registerFromImages(
        image: MultipartFile,
        userId: UUID,
    ): WishWithItem {
        val productImage = ProductImage.of(image.bytes, image.contentType)
        val extraction = productImageExtractor.extract(productImage)
        // 크롭 이미지는 부가 정보다. S3 일시 장애·타임아웃이 이미지 등록 전체를 5xx 로 깨뜨리지 않도록,
        // 업로드 실패는 imageUrl=null 로 degrade 한다 (imageUrl 없이도 등록 가능한 계약).
        val imageUrl =
            extraction.boundingBox
                ?.let { imageCropper.crop(productImage.bytes, it) }
                ?.let { cropped ->
                    runCatching {
                        imageStorage.upload(cropped, "items/${UUID.randomUUID()}.png", "image/png")
                    }.getOrElse { e ->
                        log.warn("크롭 이미지 S3 업로드 실패, imageUrl 없이 등록 진행: {}", e.message)
                        null
                    }
                }
        return wishPersistenceService.persist(userId, Item.from(extraction.snapshot.copy(imageUrl = imageUrl)))
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
}
