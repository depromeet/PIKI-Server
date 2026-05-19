package com.depromeet.team3.auth.controller.dto

import com.depromeet.team3.auth.service.dto.TokenPair
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "게스트 생성 응답")
data class GuestCreateResponse(
    @field:Schema(description = "액세스 토큰", example = "eyJhbGciOiJIUzI1NiJ9...")
    val accessToken: String,
    @field:Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiJ9...")
    val refreshToken: String,
) {
    companion object {
        fun from(tokenPair: TokenPair): GuestCreateResponse =
            GuestCreateResponse(
                accessToken = tokenPair.accessToken,
                refreshToken = tokenPair.refreshToken,
            )
    }
}
