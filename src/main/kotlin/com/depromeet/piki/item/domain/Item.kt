package com.depromeet.piki.item.domain

import com.depromeet.piki.common.domain.LongBaseEntity
import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.product.domain.ProductLinkConverter
import com.depromeet.piki.product.service.ProductSnapshot
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.slf4j.LoggerFactory

// wish 와 tournament_item 이 함께 참조하는 공유 엔티티.
// 같은 link 라도 위시·출전마다 1:1 독립 행이라, 한쪽의 수정이 다른 쪽에 번지지 않는다(수정 격리).
// link 는 바뀌면 사실상 다른 상품이라 재등록 영역으로 보고 불변(val). 단 이미지 등록 경로는 URL 이
// 없어 link 가 null 일 수 있다 (URL 추출 / 이미지 추출 두 경로가 같은 item 을 만든다).
@Entity
@Table(name = "items")
class Item(
    @Convert(converter = ProductLinkConverter::class)
    @Column(name = "source_url", nullable = true, length = 2048)
    val link: ProductLink? = null,
    name: String? = null,
    imageUrl: String? = null,
    currentPrice: Int? = null,
    currency: String? = null,
    status: ItemStatus = ItemStatus.READY,
) : LongBaseEntity() {
    // 추출 필드 — setter 직접 노출 대신 의도가 박힌 명령(markReady·recover)으로만 바꾼다.
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

    // 파싱 생애주기. PROCESSING→READY/FAILED 전이는 markReady/markFailed 로,
    // FAILED→READY 복구는 사용자 직접 보정(recover)으로 일어난다 (setter 비노출).
    // 기본값 READY 는 동기 완성 경로(Item.from)·DB 로딩 행과의 호환을 위한 것이고,
    // 비동기 등록은 Item.processing 으로 PROCESSING 을 명시한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: ItemStatus = status
        protected set

    // 엔티티 불변식 — 최후의 보루. 정상 흐름에선 입력 경계(요청 DTO 검증 등)가 먼저 거르므로 여기 닿지 않는다.
    // validate 는 범위·길이만 보고 null 은 통과시킨다 — kotlin("plugin.jpa") 가 합성하는 no-arg 생성자가
    // Hibernate 인스턴스화 시점에 이 init 을 실제로 실행하기 때문이다(필드는 그 뒤에 주입되므로 이 순간엔 전부 null).
    // 그래서 "READY ⟹ name" 같은 상태-의존 불변식은 init 에 두지 않는다 — 두면 READY 행 하이드레이션이 깨진다.
    // 그 불변식은 READY 가 되는 명시 경로(from·markReady)와 클라이언트 입력 경계(recover)에서 검사한다.
    init {
        validate(this.name, this.currentPrice, this.imageUrl, this.currency)
    }

    // 추출 필드(name·price·image·currency)를 실제로 갈아끼우는 코어. 들어온 필드만 갱신하고,
    // 생성 때와 같은 불변식을 재검증해 가변화로 우회되지 않게 한다. 외부에 노출하지 않는다 —
    // item 데이터는 "링크에서 기계 추출한 사실"이라 자유 편집 대상이 아니고, 변경은 의도가 박힌
    // 명령(markReady·recover)으로만 들어온다. 각 명령이 자기 status 전이를 책임지고 이 코어는 값만 바꾼다.
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
        log.info(
            "item {} apply: name [{}]->[{}], currentPrice [{}]->[{}], imageUrl [{}]->[{}], currency [{}]->[{}]",
            getIdOrNull(),
            this.name,
            newName,
            this.currentPrice,
            newCurrentPrice,
            this.imageUrl,
            newImageUrl,
            this.currency,
            newCurrency,
        )
        this.name = newName
        this.currentPrice = newCurrentPrice
        this.imageUrl = newImageUrl
        this.currency = newCurrency
    }

    // 클라이언트가 item 을 직접 바꾸는 유일한 통로 — 추출 실패(FAILED) 항목의 수동 보정.
    // FAILED 는 추출이 맺히다 만 미완성 스냅샷이라, 사용자가 채우면 정상 항목이 된 것이므로 READY 로 복구한다.
    // READY(등록 완료)는 기계 추출 사실이라 손으로 못 바꾸고(계약 위반 409), PROCESSING(파싱 중)은 워커 소관이라
    // 끼어들 수 없다(409). 둘 다 멀쩡한 클라이언트가 PATCH 로 닿을 수 있는 계약이므로 도메인이 직접 던진다.
    fun recover(
        name: String? = null,
        currentPrice: Int? = null,
        imageUrl: String? = null,
        currency: String? = null,
    ) {
        when (status) {
            ItemStatus.READY -> throw ItemException.alreadyReady()
            ItemStatus.PROCESSING -> throw ItemException.stillProcessing()
            ItemStatus.FAILED -> {
                apply(name = name, currentPrice = currentPrice, imageUrl = imageUrl, currency = currency)
                // 입력 경계 계약 — 보정 후에도 이름이 없으면 쓸 수 없는 상품이 READY 로 새어 들어간다(409 아닌 400).
                if (this.name.isNullOrBlank()) throw ItemException.nameRequiredForReady()
                status = ItemStatus.READY
                log.info("item {} 사용자 직접 보정으로 FAILED → READY 복구", getIdOrNull())
            }
        }
    }

    // PROCESSING → READY. 백그라운드 파싱이 성공해 추출 결과(snapshot)를 채우며 전이한다.
    // 전이 가능 상태가 아닌데 호출되면 워커가 잘못된 item 을 집은 코드 버그이므로 check(500).
    fun markReady(snapshot: ProductSnapshot) {
        check(status == ItemStatus.PROCESSING) { "PROCESSING 이 아닌 item(status=$status)은 READY 로 전이할 수 없다" }
        apply(
            name = snapshot.name,
            currentPrice = snapshot.currentPrice,
            imageUrl = snapshot.imageUrl,
            currency = snapshot.currency,
        )
        // 추출이 이름을 못 얻었으면 READY 부적격 — 워커가 이 예외를 받아 FAILED 로 흡수한다(PROCESSING 방치 방지).
        requireReadyInvariant()
        status = ItemStatus.READY
    }

    // PROCESSING → FAILED. 파싱 실패(상품 아님·신뢰 불가·타임아웃)를 동기 400 대신 상태로 남긴다.
    fun markFailed() {
        check(status == ItemStatus.PROCESSING) { "PROCESSING 이 아닌 item(status=$status)은 FAILED 로 전이할 수 없다" }
        status = ItemStatus.FAILED
        log.info("item {} 파싱 실패 → FAILED 전이", getIdOrNull())
    }

    // 파싱이 끝나 추출 결과가 채워진 상태인지. 토너먼트 출전처럼 "완성된 상품만" 을 요구하는 곳에서 쓴다.
    // PROCESSING(파싱 중)·FAILED(실패)는 false — 이름·가격이 비어 있어 출전에 부적합하다.
    fun isReady(): Boolean = status == ItemStatus.READY

    // 클라이언트 보정(recover) 대상인지 — FAILED 만 보정 가능. 서비스가 S3 업로드 같은 외부 작업 전에
    // 미리 걸러 헛된 비용(orphan 업로드)을 막는 사전 가드용. 도메인 최후 보루는 recover 가 진다.
    fun isFailed(): Boolean = status == ItemStatus.FAILED

    // READY 불변식 — "쓸 수 있는 상품"은 최소한 이름이 있어야 한다. isReady() 게이트(목록 노출·토너먼트 출전)가
    // 미완성 item 을 정상 상품으로 취급하지 않도록, READY 가 되는 명시 경로(from·markReady)에서 검사한다.
    // 엔티티 최후의 보루이므로 require(불변식)다 — 정상 흐름에선 입력 경계(recover 의 nameRequiredForReady,
    // 추출 워커의 FAILED 흡수)가 먼저 거른다. price·image·currency 는 추출이 정당하게 못 얻을 수 있어 필수로 두지 않는다.
    private fun requireReadyInvariant() {
        require(!name.isNullOrBlank()) { "READY item 은 최소한 name 이 있어야 한다 (status=$status)" }
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
        private val log = LoggerFactory.getLogger(Item::class.java)
        private const val NAME_MAX_LENGTH = 512
        private const val IMAGE_URL_MAX_LENGTH = 2048
        private const val CURRENCY_MAX_LENGTH = 8

        // 동기 완성 경로 — 이미 추출이 끝난 snapshot 으로 READY 상태 item 을 만든다 (이미지 등록 등).
        // URL 추출이든 이미지 추출이든 ProductSnapshot 으로 통일돼 들어온다. 이미지 추출은 link 가 null 일 뿐.
        fun from(snapshot: ProductSnapshot): Item =
            Item(
                link = snapshot.link,
                name = snapshot.name,
                imageUrl = snapshot.imageUrl,
                currentPrice = snapshot.currentPrice,
                currency = snapshot.currency,
                status = ItemStatus.READY,
            ).also { it.requireReadyInvariant() }

        // 비동기 등록 시작점 — link 만 가진 PROCESSING item. 파싱이 끝나면 markReady/markFailed 로 전이한다.
        fun processing(link: ProductLink): Item = Item(link = link, status = ItemStatus.PROCESSING)
    }
}
