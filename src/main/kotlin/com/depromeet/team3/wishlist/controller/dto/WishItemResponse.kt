package com.depromeet.team3.wishlist.controller.dto

import com.depromeet.team3.item.domain.Item
import com.depromeet.team3.wishlist.domain.Wish
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "위시 항목 — 위시 기록(wish)과 상품 스냅샷(item)의 묶음")
data class WishItemResponse(
    @field:Schema(description = "위시 기록")
    val wish: WishView,
    @field:Schema(description = "상품 스냅샷")
    val item: ItemView,
) {
    companion object {
        fun from(
            wish: Wish,
            item: Item,
        ): WishItemResponse =
            WishItemResponse(
                wish = WishView.from(wish),
                item = ItemView.from(item),
            )
    }

    @Schema(description = "위시 기록")
    data class WishView(
        @field:Schema(description = "위시 항목 ID", example = "1024")
        val id: Long,
        @field:Schema(description = "위시리스트에 담은 시각", example = "2026-05-21T10:00:00")
        val createdAt: LocalDateTime,
    ) {
        companion object {
            fun from(wish: Wish): WishView =
                WishView(
                    id = wish.getId(),
                    createdAt = wish.createdAt,
                )
        }
    }

    @Schema(description = "상품 스냅샷")
    data class ItemView(
        @field:Schema(description = "상품 ID", example = "512")
        val id: Long,
        @field:Schema(description = "상품명 (추출 실패 시 null)", example = "에어 조던 1 미드", nullable = true)
        val name: String?,
        @field:Schema(description = "스냅샷 시점의 현재 판매가", example = "119000", nullable = true)
        val currentPrice: Int?,
        @field:Schema(description = "통화 코드 (ISO 4217)", example = "KRW", nullable = true)
        val currency: String?,
        @field:Schema(
            description = "상품 대표 이미지 URL",
            example = "https://cdn.example.com/p/512.jpg",
            nullable = true,
        )
        val imageUrl: String?,
        @field:Schema(
            description = "원본 상품 페이지 URL",
            example = "https://www.example-shop.com/products/12345",
        )
        val sourceUrl: String,
    ) {
        companion object {
            fun from(item: Item): ItemView =
                ItemView(
                    id = item.getId(),
                    name = item.name,
                    currentPrice = item.currentPrice,
                    currency = item.currency,
                    imageUrl = item.imageUrl,
                    sourceUrl = item.link.toString(),
                )
        }
    }
}
