package com.depromeet.piki.item.domain

import com.depromeet.piki.common.domain.LongBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDateTime

// item(정체성)의 한 추출 버전. item 이 갱신될 때마다 새 행이 쌓여 가격·이름·이미지 이력을 보존한다.
// itemId 는 정체성(items) 을 raw 로 참조한다 (FK 제약 없음 — 프로젝트 정책). 같은 item 의 여러 버전이 1:N.
// 버전 순서는 id(단조증가)로 충분해 별도 version 컬럼을 두지 않는다.
// Epic #362(상품 버저닝) 1단계로 추가됐고, 아직 어디서도 참조하지 않는다 — 쓰기 이중화·참조 이전은 후속 단계.
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
    // 추출 필드 — setter 직접 노출 대신, 후속 단계에서 의도가 박힌 명령(markReady 등)으로만 바꾼다.
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

    // 이 버전의 추출 생애주기. PROCESSING(추출 중)→READY(완료)/FAILED(실패). 전이는 후속 단계에서 명령으로.
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
        require((currentPrice ?: 0) >= 0) { "currentPrice 는 음수일 수 없다: $currentPrice" }
        require((name?.length ?: 0) <= NAME_MAX_LENGTH) { "name 길이가 ${NAME_MAX_LENGTH}자를 초과했다" }
        require((imageUrl?.length ?: 0) <= IMAGE_URL_MAX_LENGTH) { "imageUrl 길이가 ${IMAGE_URL_MAX_LENGTH}자를 초과했다" }
        require((currency?.length ?: 0) <= CURRENCY_MAX_LENGTH) { "currency 길이가 ${CURRENCY_MAX_LENGTH}자를 초과했다" }
    }

    companion object {
        const val NAME_MAX_LENGTH = 512
        const val IMAGE_URL_MAX_LENGTH = 2048
        const val CURRENCY_MAX_LENGTH = 8
    }
}
