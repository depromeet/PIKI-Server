package com.depromeet.team3.wishlist.controller.dto

import com.depromeet.team3.wishlist.domain.Wish
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "위시리스트 등록 응답")
data class WishlistRegisterResponse(
    @field:Schema(description = "위시리스트 항목 ID", example = "1024")
    val wishId: Long,
    @field:Schema(description = "상품명 (추출 실패 시 null)", example = "에어 조던 1 미드", nullable = true)
    val name: String?,
    @field:Schema(description = "정가 (할인 전 가격)", example = "159000", nullable = true)
    val regularPrice: Int?,
    @field:Schema(description = "할인가", example = "119000", nullable = true)
    val discountedPrice: Int?,
    @field:Schema(description = "할인율 (%)", example = "25", nullable = true)
    val discountRate: Int?,
    @field:Schema(description = "통화 코드 (ISO 4217)", example = "KRW", nullable = true)
    val currency: String?,
    @field:Schema(
        description = "상품 대표 이미지 URL",
        example = "https://cdn.example.com/p/1024.jpg",
        nullable = true,
    )
    val imageUrl: String?,
) {
    companion object {
        fun from(wish: Wish): WishlistRegisterResponse =
            WishlistRegisterResponse(
                wishId = wish.getId(),
                name = wish.product.name,
                regularPrice = wish.product.regularPrice,
                discountedPrice = wish.product.discountedPrice,
                discountRate = wish.product.discountRate,
                currency = wish.product.currency,
                imageUrl = wish.product.imageUrl,
            )
    }
}
