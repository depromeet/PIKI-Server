package com.depromeet.piki.wishlist.controller.dto

import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.item.domain.ItemSnapshot
import com.depromeet.piki.item.domain.ItemStatus
import com.depromeet.piki.wishlist.domain.Wish
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
            snapshot: ItemSnapshot,
        ): WishItemResponse =
            WishItemResponse(
                wish = WishView.from(wish),
                item = ItemView.from(item, snapshot),
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
        @field:Schema(
            description = "파싱 상태 — PROCESSING(담는 중)/READY(완료)/FAILED(파싱 실패). PROCESSING 동안은 name·currentPrice·imageUrl 이 비어 있다.",
            example = "READY",
        )
        val status: ItemStatus,
        @field:Schema(description = "상품명 (PROCESSING·실패 시 null)", example = "에어 조던 1 미드", nullable = true)
        val name: String?,
        @field:Schema(description = "스냅샷 시점의 현재 판매가 (PROCESSING·실패 시 null)", example = "119000", nullable = true)
        val currentPrice: Int?,
        @field:Schema(description = "통화 코드 (ISO 4217)", example = "KRW", nullable = true)
        val currency: String?,
        @field:Schema(
            description = "상품 대표 이미지 URL (PROCESSING·실패 시 null)",
            example = "https://cdn.example.com/p/512.jpg",
            nullable = true,
        )
        val imageUrl: String?,
        @field:Schema(
            description = "원본 상품 페이지 URL (이미지 등록 항목은 URL 이 없어 null)",
            example = "https://www.example-shop.com/products/12345",
            nullable = true,
        )
        val sourceUrl: String?,
    ) {
        companion object {
            // 표시값(status·name·currentPrice·currency·imageUrl)은 활성 snapshot 에서,
            // 정체성(id·sourceUrl=상품 링크)은 item 에서 읽는다. snapshot 은 5단계 갱신에서 새 버전으로 스왑된다.
            fun from(
                item: Item,
                snapshot: ItemSnapshot,
            ): ItemView =
                ItemView(
                    id = item.getId(),
                    status = snapshot.status,
                    name = snapshot.name,
                    currentPrice = snapshot.currentPrice,
                    currency = snapshot.currency,
                    imageUrl = snapshot.imageUrl,
                    sourceUrl = item.link?.toString(),
                )
        }
    }
}
