package com.depromeet.team3.item.domain

import com.depromeet.team3.common.domain.LongBaseEntity
import com.depromeet.team3.product.domain.ProductLink
import com.depromeet.team3.product.domain.ProductLinkConverter
import com.depromeet.team3.product.service.ProductSnapshot
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Table

// wish 와 tournament_item 이 함께 참조하는 공유 엔티티.
// 가격·이름·이미지는 추출 시점 스냅샷이라 같은 link 라도 행을 합치지 않는다.
@Entity
@Table(name = "items")
class Item(
    @Convert(converter = ProductLinkConverter::class)
    @Column(name = "source_url", nullable = false, length = 2048)
    val link: ProductLink,
    @Column(name = "name", length = 512)
    val name: String? = null,
    @Column(name = "image_url", length = 2048)
    val imageUrl: String? = null,
    @Column(name = "current_price")
    val currentPrice: Int? = null,
    @Column(name = "currency", length = 8)
    val currency: String? = null,
) : LongBaseEntity() {
    // 엔티티 불변식 — 최후의 보루. 정상 흐름에선 입력 경계(toProductSnapshot 등)가 먼저
    // 거르므로 여기 닿지 않는다. 닿으면 어떤 경계가 검증을 빠뜨린 코드 버그다.
    // (kotlin-noarg 가 합성하는 JPA 생성자는 이 init 을 우회하므로 DB 로딩 행은 검증 대상이 아니다.)
    init {
        require((currentPrice ?: 0) >= 0) { "currentPrice 는 음수일 수 없다: $currentPrice" }
        require((name?.length ?: 0) <= NAME_MAX_LENGTH) { "name 길이가 ${NAME_MAX_LENGTH}자를 초과했다" }
        require((imageUrl?.length ?: 0) <= IMAGE_URL_MAX_LENGTH) { "imageUrl 길이가 ${IMAGE_URL_MAX_LENGTH}자를 초과했다" }
        require((currency?.length ?: 0) <= CURRENCY_MAX_LENGTH) { "currency 길이가 ${CURRENCY_MAX_LENGTH}자를 초과했다" }
    }

    companion object {
        private const val NAME_MAX_LENGTH = 512
        private const val IMAGE_URL_MAX_LENGTH = 2048
        private const val CURRENCY_MAX_LENGTH = 8

        fun from(snapshot: ProductSnapshot): Item =
            Item(
                link = snapshot.link,
                name = snapshot.name,
                imageUrl = snapshot.imageUrl,
                currentPrice = snapshot.currentPrice,
                currency = snapshot.currency,
            )
    }
}
