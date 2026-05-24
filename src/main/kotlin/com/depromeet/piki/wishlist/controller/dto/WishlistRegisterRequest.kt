package com.depromeet.piki.wishlist.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "위시리스트 등록 요청")
data class WishlistRegisterRequest(
    @field:NotBlank
    @field:Size(max = 2048)
    @field:Schema(
        description = "등록할 상품 페이지 URL",
        example = "https://www.example-shop.com/products/12345",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val url: String,
)
