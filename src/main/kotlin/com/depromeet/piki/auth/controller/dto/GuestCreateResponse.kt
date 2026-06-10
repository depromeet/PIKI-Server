package com.depromeet.piki.auth.controller.dto

import com.depromeet.piki.auth.service.dto.TokenPair
import com.depromeet.piki.auth.web.TokenCarrying
import com.depromeet.piki.user.controller.dto.UserResponse
import com.depromeet.piki.user.domain.User
import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema

// TokenCarrying — TokenCookieResponseAdvice 가 (WEB 이면) 쿠키 set 후 body 토큰을 비운다.
// accessToken/refreshToken 은 tokenPair 에서 파생되며 bodyTokensIncluded=false 일 때 null 이 된다.
// 따라서 contract 상 두 토큰은 nullable: APP=값 / WEB=null(쿠키로 전달).
@Schema(description = "게스트 생성 응답")
data class GuestCreateResponse(
    @field:Schema(description = "서버가 fill 한 초기 유저 정보. FE 가 확정/수정 UI 의 초기값으로 사용.")
    val user: UserResponse,
    @get:JsonIgnore
    override val tokenPair: TokenPair,
    @get:JsonIgnore
    val bodyTokensIncluded: Boolean = true,
) : TokenCarrying {
    @get:Schema(description = "액세스 토큰 (APP=값 / WEB=null, 쿠키로 전달)", nullable = true, example = "eyJhbGciOiJIUzI1NiJ9...")
    val accessToken: String? get() = tokenPair.accessToken.takeIf { bodyTokensIncluded }

    @get:Schema(description = "리프레시 토큰 (APP=값 / WEB=null, 쿠키로 전달)", nullable = true, example = "eyJhbGciOiJIUzI1NiJ9...")
    val refreshToken: String? get() = tokenPair.refreshToken.takeIf { bodyTokensIncluded }

    override fun withoutBodyTokens(): TokenCarrying = copy(bodyTokensIncluded = false)

    companion object {
        fun from(
            tokenPair: TokenPair,
            user: User,
        ): GuestCreateResponse = GuestCreateResponse(user = UserResponse.from(user), tokenPair = tokenPair)
    }
}
