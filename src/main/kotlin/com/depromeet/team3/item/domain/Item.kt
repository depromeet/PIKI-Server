package com.depromeet.team3.item.domain

import com.depromeet.team3.common.domain.LongBaseEntity
import com.depromeet.team3.product.domain.ProductLink
import com.depromeet.team3.product.domain.ProductLinkConverter
import com.depromeet.team3.product.service.ProductDetails
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
    companion object {
        fun from(details: ProductDetails): Item =
            Item(
                link = details.link,
                name = details.name,
                imageUrl = details.imageUrl,
                currentPrice = details.currentPrice,
                currency = details.currency,
            )
    }
}
