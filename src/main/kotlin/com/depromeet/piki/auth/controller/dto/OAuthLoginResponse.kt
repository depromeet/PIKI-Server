package com.depromeet.piki.auth.controller.dto

import com.depromeet.piki.auth.service.dto.TokenPair
import com.depromeet.piki.auth.web.TokenCarrying
import com.depromeet.piki.user.controller.dto.UserResponse
import com.depromeet.piki.user.domain.User
import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema

// TokenCarrying — guest 응답과 동일하게 #166 advice 가 (WEB 이면) 쿠키 set 후 body 토큰을 비운다.
// 토큰은 nullable: APP=값 / WEB=null(쿠키로 전달). user 는 로그인/가입된 회원 정보.
@Schema(description = "소셜 로그인 응답")
data class OAuthLoginResponse(
    @field:Schema(description = "로그인/가입된 유저 정보. 신규 가입이면 자동 fill 된 닉네임이 온다.")
    val user: UserResponse,
    @get:JsonIgnore
    override val tokenPair: TokenPair,
    @get:JsonIgnore
    val bodyTokensIncluded: Boolean = true,
) : TokenCarrying {
    @get:Schema(description = "액세스 토큰 (APP=값 / WEB=null, 쿠키로 전달)", nullable = true, example = "eyJhbGciOiJIUzI1NiJ9...")
    val accessToken: String? get() = tokenPair.accessToken.takeIf { bodyTokensIncluded }

    @get:Schema(
        description = "리프레시 토큰 (APP=값 / WEB=null, 쿠키로 전달)",
        nullable = true,
        example = "eyJhbGciOiJIUzI1NiJ9...",
    )
    val refreshToken: String? get() = tokenPair.refreshToken.takeIf { bodyTokensIncluded }

    override fun withoutBodyTokens(): TokenCarrying = copy(bodyTokensIncluded = false)

    companion object {
        fun from(
            tokenPair: TokenPair,
            user: User,
        ): OAuthLoginResponse = OAuthLoginResponse(user = UserResponse.from(user), tokenPair = tokenPair)
    }
}
