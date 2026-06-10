package com.depromeet.piki.tournament.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.springframework.web.multipart.MultipartFile

@Schema(description = "토너먼트 아이템 수정 요청 — 들어온 필드만 갱신한다")
data class UpdateTournamentItemRequest(
    @field:Schema(description = "수정할 상품명", example = "나이키 에어맥스", nullable = true)
    @field:Size(min = 1, max = 512)
    val name: String? = null,
    @field:Schema(description = "수정할 현재 판매가", example = "129000", nullable = true)
    @field:Min(0)
    val price: Int? = null,
    @field:Schema(description = "수정할 통화 코드 (ISO 4217)", example = "KRW", nullable = true)
    @field:Size(max = 8)
    val currency: String? = null,
    @field:Schema(description = "수정할 상품 이미지", nullable = true)
    val image: MultipartFile? = null,
)
