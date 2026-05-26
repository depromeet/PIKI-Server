package com.depromeet.piki.tournament.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AddTournamentItemFromLinkRequest(
    @field:NotBlank
    @field:Size(max = 2048)
    @field:Schema(
        description = "등록할 상품 페이지 URL",
        example = "https://www.example-shop.com/products/12345",
        requiredMode = RequiredMode.REQUIRED,
    )
    val url: String,
)
