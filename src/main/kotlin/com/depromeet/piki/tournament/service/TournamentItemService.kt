package com.depromeet.piki.tournament.service

import com.depromeet.piki.common.storage.ImageStorage
import com.depromeet.piki.image.domain.ProductImage
import com.depromeet.piki.item.repository.ItemSnapshotRepository
import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.tournament.repository.TournamentItemRepository
import com.depromeet.piki.tournament.repository.TournamentRepository
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Service
class TournamentItemService(
    private val tournamentItemPersistenceService: TournamentItemPersistenceService,
    private val imageStorage: ImageStorage,
    private val tournamentRepository: TournamentRepository,
    private val tournamentItemRepository: TournamentItemRepository,
    private val itemSnapshotRepository: ItemSnapshotRepository,
) {
    fun addItemFromLink(
        userId: UUID,
        tournamentId: Long,
        url: String,
    ): Long {
        val link = ProductLink.parse(url)
        // fetch 불가 플랫폼(봇 차단)은 담아봐야 파싱이 무의미하게 실패한다 — 등록 시점에 막아 빠르게 안내한다(400).
        link.verifySupportedPlatform()
        // URL 경로는 PENDING snapshot 을 커밋만 하고(outbox 적재) 즉시 반환한다. 파싱은 디스패처(@Scheduled)가
        // PENDING 을 집어 워커에 넘긴다 — @Async 유실과 무관하게 최소 1회는 claim 된다(at-least-once).
        // 파싱·상태 전이는 item PK 를, 클라이언트 응답은 tournament_item PK 를 쓴다 (PersistedTournamentItem).
        val persisted = tournamentItemPersistenceService.persistLinkItem(userId, tournamentId, link)
        return persisted.tournamentItemId
    }

    fun addItemsFromImages(
        userId: UUID,
        tournamentId: Long,
        images: List<MultipartFile>,
    ): List<Long> {
        if (images.size !in MIN_IMAGE_COUNT..MAX_IMAGE_COUNT) throw TournamentException.invalidImageCount()
        // 권한·상태·복제를 업로드 전에 미리 검증 — 거부될 요청이 S3 에 orphan raw 를 남기지 않게 한다(정원 동시성 최종 검증은 persist 의 FOR UPDATE).
        tournamentItemPersistenceService.verifyCanAddItems(userId, tournamentId)
        // 형식 검증(빈 바이트·미지원 MIME) — 실패 시 즉시 400. 유효한 이미지만 durable 적재한다.
        val productImages = images.map { ProductImage.of(it.bytes, it.contentType) }
        // 원본을 S3 raw 에 올려 입력을 durable 화한다(외부 호출, 트랜잭션 밖). 이 key 가 item 의 입력 정체성이 된다.
        val imageKeys = productImages.map { uploadRaw(it) }
        // 파싱·상태 전이는 item PK 를, 클라이언트 응답은 tournament_item PK 를 쓴다 (PersistedTournamentItem).
        val persisted = tournamentItemPersistenceService.persistPendingImageItems(userId, tournamentId, imageKeys)
        return persisted.map { it.tournamentItemId }
    }

    // 원본 이미지를 S3 raw prefix 에 올리고 그 object key 를 돌려준다(워커가 download(key)로 다시 읽는다). 파싱이 끝나면 워커가 회수한다.
    private fun uploadRaw(image: ProductImage): String {
        val key = "items/raw/${UUID.randomUUID()}.${image.extension}"
        imageStorage.upload(image.bytes, key, image.mimeType)
        return key
    }

    // recoverWishItem 과 동일한 패턴 — 이미지 형식 검증 후 S3 업로드는 트랜잭션 밖에서,
    // 권한·상태 검증 + snapshot.recover() 는 recoverItem(@Transactional) 에 위임한다.
    // S3 업로드 전 출전 시점 snapshot 상태를 사전 확인해 orphan 업로드를 방지한다(도메인 최후 보루는 recoverItem).
    fun updateItem(
        userId: UUID,
        tournamentId: Long,
        tournamentItemId: Long,
        name: String?,
        price: Int?,
        currency: String?,
        image: MultipartFile?,
    ) {
        val productImage = image?.let { ProductImage.of(it.bytes, it.contentType) }
        val tournament =
            tournamentRepository.findTournamentById(tournamentId)
                ?: throw TournamentException.notFoundTournament()
        if (!tournament.isPending()) throw TournamentException.notPendingTournament()
        val tournamentItem =
            tournamentItemRepository.findById(tournamentItemId)
                ?: throw TournamentException.notFoundTournamentItem()
        if (tournamentItem.tournamentId != tournamentId) throw TournamentException.notFoundTournamentItem()
        if (tournamentItem.userId != userId) throw TournamentException.forbiddenTournament()
        // 출전 시점 고정 snapshot 으로 사전 상태 검증 — FAILED 가 아니면 S3 업로드 전에 막는다(orphan 방지).
        // recover 가 READY/PROCESSING 에 사유별 409 를 던진다(트랜잭션 밖 조회라 던지기 전용, 실제 보정은 recoverItem).
        val snapshotId = tournamentItem.snapshotId
        val snapshot =
            itemSnapshotRepository.findById(snapshotId)
                ?: error("snapshot 없음 — tournamentItemId=$tournamentItemId, snapshotId=$snapshotId")
        if (!snapshot.isFailed()) snapshot.recover()
        val imageUrl =
            productImage?.let {
                imageStorage.upload(it.bytes, "tournament-items/${UUID.randomUUID()}.${it.extension}", it.mimeType)
            }
        tournamentItemPersistenceService.recoverItem(userId, tournamentId, tournamentItemId, name, price, imageUrl, currency)
    }

    companion object {
        private const val MIN_IMAGE_COUNT = 1
        private const val MAX_IMAGE_COUNT = 5
    }
}
