package com.depromeet.piki.auth.controller.dto

import com.depromeet.piki.auth.service.dto.TokenPair
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "토큰 갱신 응답")
data class TokenRefreshResponse(
    @field:Schema(description = "새 액세스 토큰", example = "eyJhbGciOiJIUzI1NiJ9...")
    val accessToken: String,
    @field:Schema(description = "새 리프레시 토큰", example = "eyJhbGciOiJIUzI1NiJ9...")
    val refreshToken: String,
) {
    companion object {
        fun from(tokenPair: TokenPair): TokenRefreshResponse =
            TokenRefreshResponse(
                accessToken = tokenPair.accessToken,
                refreshToken = tokenPair.refreshToken,
            )
    }
}
