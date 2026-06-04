package com.depromeet.piki.item.domain

import com.depromeet.piki.common.domain.LongBaseEntity
import com.depromeet.piki.product.service.ProductSnapshot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDateTime

// item(정체성)의 한 추출 버전. item 이 갱신될 때마다 새 행이 쌓여 가격·이름·이미지 이력을 보존한다.
// itemId 는 정체성(items) 을 raw 로 참조한다 (FK 제약 없음 — 프로젝트 정책). 같은 item 의 여러 버전이 1:N.
// 버전 순서는 id(단조증가)로 충분해 별도 version 컬럼을 두지 않는다.
//
// 2단계(쓰기 이중화)에선 snapshot 이 item 의 생애주기를 1:1 평행 추적한다 — 등록 시 PROCESSING 으로 함께 생기고,
// 추출 성공/실패/보정에 맞춰 item 과 같이 전이한다. item 당 snapshot 1행이며, 진짜 여러 버전은 5단계 갱신부터 쌓인다.
// 전이의 계약 검증(이미 READY 등 클라이언트 도달 가능 예외)은 item 이 책임지고, snapshot 은 item 이 같은 전이를
// 통과한 뒤 호출되므로 check 불변식(상태 가정)만 둔다.
@Entity
@Table(name = "item_snapshots")
class ItemSnapshot(
    @Column(name = "item_id", nullable = false)
    val itemId: Long,
    name: String? = null,
    imageUrl: String? = null,
    currentPrice: Int? = null,
    currency: String? = null,
    status: ItemStatus = ItemStatus.PROCESSING,
    extractedAt: LocalDateTime? = null,
) : LongBaseEntity() {
    // 추출 필드 — setter 직접 노출 대신, 의도가 박힌 명령(markReady·markFailed·recover)으로만 바꾼다.
    @Column(name = "name", length = 512)
    var name: String? = name
        protected set

    @Column(name = "image_url", length = 2048)
    var imageUrl: String? = imageUrl
        protected set

    @Column(name = "current_price")
    var currentPrice: Int? = currentPrice
        protected set

    @Column(name = "currency", length = 8)
    var currency: String? = currency
        protected set

    // 이 버전의 추출 생애주기. PROCESSING(추출 중)→READY(완료)/FAILED(실패), FAILED→READY(보정).
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: ItemStatus = status
        protected set

    // 추출이 완료(READY)된 시각. PROCESSING 동안은 비어 있다(null). 정체성 row 생성 시각(created_at)과 구분된다.
    @Column(name = "extracted_at")
    var extractedAt: LocalDateTime? = extractedAt
        protected set

    // 엔티티 불변식 — 최후의 보루. 범위·길이만 보고 null 은 통과시킨다.
    // kotlin("plugin.jpa") 가 합성하는 no-arg 생성자가 Hibernate 하이드레이션 시점에 이 init 을 실행하는데,
    // 그 순간 필드는 아직 주입 전이라 전부 null/기본값이다. 그래서 상태-의존·필수값 불변식은 여기 두지 않고
    // (두면 행 하이드레이션이 깨진다), 범위·길이만 본다. Item 과 동일 전략.
    init {
        validate(name, currentPrice, imageUrl, currency)
    }

    // 추출 필드를 갈아끼우는 코어. 들어온 필드만 갱신하고 생성 때와 같은 불변식을 재검증한다. Item.apply 와 동형.
    private fun apply(
        name: String? = null,
        currentPrice: Int? = null,
        imageUrl: String? = null,
        currency: String? = null,
    ) {
        val newName = name ?: this.name
        val newCurrentPrice = currentPrice ?: this.currentPrice
        val newImageUrl = imageUrl ?: this.imageUrl
        val newCurrency = currency ?: this.currency
        validate(newName, newCurrentPrice, newImageUrl, newCurrency)
        this.name = newName
        this.currentPrice = newCurrentPrice
        this.imageUrl = newImageUrl
        this.currency = newCurrency
    }

    // PROCESSING → READY. item.markReady 와 평행. 추출 결과(snapshot)를 채우며 전이한다.
    // extractedAt 은 호출자(서비스)가 주입한다 — 도메인이 now() 를 만들지 않아 시간 의존을 피한다.
    fun markReady(
        snapshot: ProductSnapshot,
        extractedAt: LocalDateTime,
    ) {
        check(status == ItemStatus.PROCESSING) { "PROCESSING 이 아닌 snapshot(status=$status)은 READY 로 전이할 수 없다" }
        apply(
            name = snapshot.name,
            currentPrice = snapshot.currentPrice,
            imageUrl = snapshot.imageUrl,
            currency = snapshot.currency,
        )
        requireReadyInvariant()
        status = ItemStatus.READY
        this.extractedAt = extractedAt
    }

    // PROCESSING → FAILED. 파싱 실패를 상태로 남긴다. item.markFailed 와 평행.
    fun markFailed() {
        check(status == ItemStatus.PROCESSING) { "PROCESSING 이 아닌 snapshot(status=$status)은 FAILED 로 전이할 수 없다" }
        status = ItemStatus.FAILED
    }

    // FAILED → READY. 사용자 수동 보정(item.recover)과 평행. 계약(이미 READY·PROCESSING)은 item 이 막고,
    // snapshot 은 item 이 보정 게이트를 통과한 뒤 호출되므로 check(FAILED) 불변식만 둔다.
    fun recover(
        name: String?,
        currentPrice: Int?,
        imageUrl: String?,
        currency: String?,
        extractedAt: LocalDateTime,
    ) {
        check(status == ItemStatus.FAILED) { "FAILED 가 아닌 snapshot(status=$status)은 recover 로 전이할 수 없다" }
        apply(name = name, currentPrice = currentPrice, imageUrl = imageUrl, currency = currency)
        requireReadyInvariant()
        status = ItemStatus.READY
        this.extractedAt = extractedAt
    }

    // READY 불변식 — "쓸 수 있는 버전"은 최소한 이름이 있어야 한다. Item.requireReadyInvariant 와 동형.
    private fun requireReadyInvariant() {
        require(!name.isNullOrBlank()) { "READY snapshot 은 최소한 name 이 있어야 한다 (status=$status)" }
    }

    private fun validate(
        name: String?,
        currentPrice: Int?,
        imageUrl: String?,
        currency: String?,
    ) {
        require((currentPrice ?: 0) >= 0) { "currentPrice 는 음수일 수 없다: $currentPrice" }
        require((name?.length ?: 0) <= NAME_MAX_LENGTH) { "name 길이가 ${NAME_MAX_LENGTH}자를 초과했다" }
        require((imageUrl?.length ?: 0) <= IMAGE_URL_MAX_LENGTH) { "imageUrl 길이가 ${IMAGE_URL_MAX_LENGTH}자를 초과했다" }
        require((currency?.length ?: 0) <= CURRENCY_MAX_LENGTH) { "currency 길이가 ${CURRENCY_MAX_LENGTH}자를 초과했다" }
    }

    companion object {
        const val NAME_MAX_LENGTH = 512
        const val IMAGE_URL_MAX_LENGTH = 2048
        const val CURRENCY_MAX_LENGTH = 8

        // 신규 등록 시점, item 의 현재 상태를 그대로 미러링한 snapshot — item.processing/from 과 평행하게 같은 트랜잭션에서 생성된다.
        // persist 는 PROCESSING 전용이 아니라 어떤 상태의 item 이든 받으므로(예: 픽스처의 FAILED item), status 를 고정하지 않고 item 을 따른다.
        // 등록 경로의 item 은 추출 전이라 추출 필드가 비어 있고, extractedAt 은 item 에 없는 개념이라 null 로 둔다.
        fun forItem(item: Item): ItemSnapshot =
            ItemSnapshot(
                itemId = item.getId(),
                name = item.name,
                imageUrl = item.imageUrl,
                currentPrice = item.currentPrice,
                currency = item.currency,
                status = item.status,
            )
    }
}
