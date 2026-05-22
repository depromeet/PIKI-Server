package com.depromeet.team3.wishlist.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

@Schema(description = "위시 항목 수정 요청 — 들어온 필드만 갱신한다")
data class WishlistUpdateRequest(
    @field:Schema(description = "수정할 상품명", example = "에어 조던 1 미드", nullable = true)
    @field:Size(max = 512, message = "상품명은 512자를 초과할 수 없습니다.")
    val name: String? = null,
    @field:Schema(description = "수정할 현재 판매가", example = "119000", nullable = true)
    @field:Min(value = 0, message = "가격은 0 이상이어야 합니다.")
    val currentPrice: Int? = null,
)
