package com.depromeet.piki.wishlist.service

import com.depromeet.piki.common.storage.ImageStorage
import com.depromeet.piki.image.domain.ProductImage
import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.item.repository.ItemRepository
import com.depromeet.piki.item.repository.ItemSnapshotRepository
import com.depromeet.piki.item.service.ImageParsingWorker
import com.depromeet.piki.item.service.ItemParsingService
import com.depromeet.piki.item.service.ItemParsingWorker
import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.wishlist.domain.WishCursor
import com.depromeet.piki.wishlist.domain.WishDeleteIds
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
    private val imageStorage: ImageStorage,
    private val wishRepository: WishRepository,
    private val itemRepository: ItemRepository,
    private val itemSnapshotRepository: ItemSnapshotRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // registerFromUrl 는 외부 LLM 호출(read-timeout 60s)을 동기로 기다리지 않는다.
    // link 만 가진 PROCESSING item 을 즉시 저장해 응답을 돌려주고(클라이언트는 "담는 중" 표시),
    // 실제 파싱은 itemParsingWorker 가 백그라운드에서 수행해 READY/FAILED 로 전이시킨다.
    // URL 형식 같은 계약 위반은 ProductLink.parse 가 동기로 거른다(400). 파싱 결과 실패만 FAILED 로 간다.
    fun registerFromUrl(
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

    // 이미지 등록은 registerFromUrl(link)와 같은 비동기 흐름 — 입력이 이미지(다건)일 뿐이다.
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
        // 표시값은 wish 의 활성 snapshot 에서 읽는다. snapshot 은 등록 시 함께 생기고 wish.snapshotId 로 고정된다.
        val snapshotsById =
            itemSnapshotRepository.findByIds(pageWishes.mapNotNull { it.snapshotId }).associateBy { it.getId() }
        val entries =
            pageWishes.map { wish ->
                // item·snapshot 은 wish 와 함께 영속화되며 별도 삭제 경로가 없다. 없으면 영속화 경로가 깨진 코드 버그다.
                val item = itemsById[wish.itemId] ?: error("wish ${wish.getId()} 의 item ${wish.itemId} 가 없다")
                val snapshot =
                    wish.snapshotId?.let { snapshotsById[it] }
                        ?: error("wish ${wish.getId()} 의 snapshot ${wish.snapshotId} 가 없다")
                // snapshot_id 는 FK 없는 raw Long 이라 잘못 연결되면 item 은 A, 표시값은 B 인 섞인 응답이 나갈 수 있다.
                // 서비스가 보장하는 정합성을 불변식으로 한 번 더 막는다(어긋나면 영속화 경로가 깨진 코드 버그).
                require(snapshot.itemId == wish.itemId) {
                    "wish ${wish.getId()} 의 snapshot(${snapshot.getId()}) 가 다른 item(${snapshot.itemId} != ${wish.itemId})을 가리킨다"
                }
                WishWithItem(wish = wish, item = item, snapshot = snapshot)
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
        // wish 가 가리키는 item·snapshot 은 반드시 존재한다. 없으면 영속화 경로가 깨진 코드 버그다.
        val item = itemRepository.findById(wish.itemId) ?: error("wish ${wish.getId()} 의 item ${wish.itemId} 가 없다")
        val snapshot =
            wish.snapshotId?.let { itemSnapshotRepository.findById(it) }
                ?: error("wish ${wish.getId()} 의 snapshot ${wish.snapshotId} 가 없다")
        // snapshot_id 는 FK 없는 raw Long 이라 잘못 연결되면 item 은 A, 표시값은 B 인 섞인 응답이 나갈 수 있다.
        require(snapshot.itemId == wish.itemId) {
            "wish ${wish.getId()} 의 snapshot(${snapshot.getId()}) 가 다른 item(${snapshot.itemId} != ${wish.itemId})을 가리킨다"
        }
        return WishWithItem(wish = wish, item = item, snapshot = snapshot)
    }

    // 추출 실패(FAILED) item 을 사용자가 직접 보정해 READY 로 복구한다. 이미지를 함께 주면 그대로 S3 에 올려
    // imageUrl 을 채운다(추출·크롭 없음 — 사용자가 고른 이미지를 그대로 대표 이미지로). READY·PROCESSING 은
    // recover 가 409 로 막는다. 외부 호출(S3)을 트랜잭션에 넣지 않기 위해 검증·소유권·상태 사전확인·업로드를
    // 트랜잭션 밖에서 끝내고, 영속화만 wishPersistenceService.recoverItem(@Transactional)에 위임한다.
    fun recoverWishItem(
        userId: UUID,
        wishId: Long,
        name: String?,
        currentPrice: Int?,
        currency: String?,
        image: MultipartFile?,
    ): WishWithItem {
        // 이미지 형식 검증(빈 바이트·미지원 MIME) — 외부 호출 전에 동기로 거른다(400).
        val productImage = image?.let { ProductImage.of(it.bytes, it.contentType) }
        val wish = wishRepository.findById(wishId) ?: throw WishException.notFound()
        wish.verifyOwnedBy(userId)
        // wish 가 가리키는 item 은 반드시 존재한다. 없으면 영속화 경로가 깨진 코드 버그다.
        val item = itemRepository.findById(wish.itemId) ?: error("wish ${wish.getId()} 의 item ${wish.itemId} 가 없다")
        // FAILED 가 아니면 S3 에 올리기 전에 막는다(orphan 업로드 방지). recover 가 사유별 409 를 던진다.
        if (!item.isFailed()) item.recover()
        // 이미지가 있으면 S3 업로드(트랜잭션 밖). 실패 시 ImageStorageException(502).
        val imageUrl =
            productImage?.let {
                imageStorage.upload(it.bytes, "items/${UUID.randomUUID()}.${it.extension}", it.mimeType)
            }
        val recovered = wishPersistenceService.recoverItem(wish.itemId, name, currentPrice, imageUrl, currency)
        // recoverItem 이 같은 트랜잭션에서 활성 snapshot 도 보정해 두었다. 응답 표시값은 그 snapshot 에서 읽는다.
        val snapshot =
            wish.snapshotId?.let { itemSnapshotRepository.findById(it) }
                ?: error("wish ${wish.getId()} 의 snapshot ${wish.snapshotId} 가 없다")
        return WishWithItem(wish = wish, item = recovered, snapshot = snapshot)
    }

    // 멱등 삭제: 없거나 이미 삭제됐으면 "이미 목표 상태(없음)"이므로 성공으로 본다(no-op).
    // 단 존재하는 위시가 남의 것이면 소유권은 보안 경계라 403 으로 막는다.
    @Transactional
    fun deleteWish(
        userId: UUID,
        wishId: Long,
    ) {
        val wish = wishRepository.findById(wishId) ?: return
        wish.verifyOwnedBy(userId)
        wish.delete()
    }

    // 여러 위시를 한 번에 멱등 삭제한다. 없거나 이미 삭제된 id 는 조회에서 빠져 자연히 무시된다(목표 상태 달성).
    // 존재하는 것 중 남의 위시가 하나라도 있으면 소유권 경계로 403, @Transactional 이라 본인 것도 함께 롤백된다.
    @Transactional
    fun deleteWishes(
        userId: UUID,
        wishIds: WishDeleteIds,
    ) {
        // WishDeleteIds 가 distinct·개수(1~100) 검증을 끝낸 값이라 여기선 조회·소유검증·삭제만 한다.
        val wishes = wishRepository.findAllByIds(wishIds.values)
        wishes.forEach { it.verifyOwnedBy(userId) }
        wishes.forEach { it.delete() }
    }

    companion object {
        private const val MIN_IMAGE_COUNT = 1
        private const val MAX_IMAGE_COUNT = 5
    }
}
