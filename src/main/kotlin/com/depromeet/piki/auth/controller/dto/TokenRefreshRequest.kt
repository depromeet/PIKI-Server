package com.depromeet.piki.auth.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "토큰 갱신 요청")
data class TokenRefreshRequest(
    @field:NotBlank(message = "다시 로그인해 주세요.")
    @field:Schema(description = "리프레시 토큰", requiredMode = Schema.RequiredMode.REQUIRED)
    val refreshToken: String,
)
