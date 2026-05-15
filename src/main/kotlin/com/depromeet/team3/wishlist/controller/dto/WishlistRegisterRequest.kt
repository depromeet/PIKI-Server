package com.depromeet.team3.wishlist.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.UUID

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
    @field:NotNull
    @field:Schema(
        description = "유저 식별자 (게스트 발급 API로 받은 UUID)",
        example = "8f1a3c2b-9d44-4e2a-9b12-1a2b3c4d5e6f",
        format = "uuid",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val userId: UUID,
)
