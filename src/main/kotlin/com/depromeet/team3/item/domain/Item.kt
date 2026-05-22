package com.depromeet.team3.item.domain

import com.depromeet.team3.common.domain.LongBaseEntity
import com.depromeet.team3.product.domain.ProductLink
import com.depromeet.team3.product.domain.ProductLinkConverter
import com.depromeet.team3.product.service.ProductSnapshot
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.slf4j.LoggerFactory

// wish 와 tournament_item 이 함께 참조하는 공유 엔티티.
// 같은 link 라도 위시·출전마다 1:1 독립 행이라, 한쪽의 수정이 다른 쪽에 번지지 않는다(수정 격리).
@Entity
@Table(name = "items")
class Item(
    @Convert(converter = ProductLinkConverter::class)
    @Column(name = "source_url", nullable = false, length = 2048)
    val link: ProductLink,
    name: String? = null,
    @Column(name = "image_url", length = 2048)
    val imageUrl: String? = null,
    currentPrice: Int? = null,
    @Column(name = "currency", length = 8)
    val currency: String? = null,
) : LongBaseEntity() {
    // 사용자가 수정하는 필드 — setter 직접 노출 대신 update() 로만 바꾼다.
    @Column(name = "name", length = 512)
    var name: String? = name
        protected set

    @Column(name = "current_price")
    var currentPrice: Int? = currentPrice
        protected set

    // 엔티티 불변식 — 최후의 보루. 정상 흐름에선 입력 경계(요청 DTO 검증 등)가 먼저
    // 거르므로 여기 닿지 않는다. 닿으면 어떤 경계가 검증을 빠뜨린 코드 버그다.
    // (kotlin-noarg 가 합성하는 JPA 생성자는 이 init 을 우회하므로 DB 로딩 행은 검증 대상이 아니다.)
    init {
        validate(this.name, this.currentPrice)
        require((imageUrl?.length ?: 0) <= IMAGE_URL_MAX_LENGTH) { "imageUrl 길이가 ${IMAGE_URL_MAX_LENGTH}자를 초과했다" }
        require((currency?.length ?: 0) <= CURRENCY_MAX_LENGTH) { "currency 길이가 ${CURRENCY_MAX_LENGTH}자를 초과했다" }
    }

    // 이유 불문 사용자가 item 정보를 바꾸는 통로 (LLM 추출 오류 보정이든 단순 변경이든).
    // 들어온 필드만 갱신하고, 생성 때와 같은 불변식을 재검증해 가변화로 우회되지 않게 한다.
    // wish·tournament 어느 통로로 수정하든 이 메서드를 거쳐 같은 검증·로그를 공유한다.
    fun update(
        name: String? = null,
        currentPrice: Int? = null,
    ) {
        val newName = name ?: this.name
        val newCurrentPrice = currentPrice ?: this.currentPrice
        validate(newName, newCurrentPrice)
        log.info(
            "item {} update: name [{}]->[{}], currentPrice [{}]->[{}]",
            getIdOrNull(),
            this.name,
            newName,
            this.currentPrice,
            newCurrentPrice,
        )
        this.name = newName
        this.currentPrice = newCurrentPrice
    }

    private fun validate(
        name: String?,
        currentPrice: Int?,
    ) {
        require((currentPrice ?: 0) >= 0) { "currentPrice 는 음수일 수 없다: $currentPrice" }
        require((name?.length ?: 0) <= NAME_MAX_LENGTH) { "name 길이가 ${NAME_MAX_LENGTH}자를 초과했다" }
    }

    companion object {
        private val log = LoggerFactory.getLogger(Item::class.java)
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
