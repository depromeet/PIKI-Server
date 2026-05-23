package com.depromeet.piki.auth.controller.dto

import com.depromeet.piki.auth.service.dto.TokenPair
import com.depromeet.piki.user.controller.dto.UserResponse
import com.depromeet.piki.user.domain.User
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "게스트 생성 응답")
data class GuestCreateResponse(
    @field:Schema(description = "액세스 토큰", example = "eyJhbGciOiJIUzI1NiJ9...")
    val accessToken: String,
    @field:Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiJ9...")
    val refreshToken: String,
    @field:Schema(description = "서버가 fill 한 초기 유저 정보. FE 가 확정/수정 UI 의 초기값으로 사용.")
    val user: UserResponse,
) {
    companion object {
        fun from(
            tokenPair: TokenPair,
            user: User,
        ): GuestCreateResponse =
            GuestCreateResponse(
                accessToken = tokenPair.accessToken,
                refreshToken = tokenPair.refreshToken,
                user = UserResponse.from(user),
            )
    }
}
