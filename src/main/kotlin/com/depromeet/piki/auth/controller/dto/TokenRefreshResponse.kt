package com.depromeet.piki.auth.controller.dto

import com.depromeet.piki.auth.service.dto.TokenPair
import com.depromeet.piki.auth.web.TokenCarrying
import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema

// TokenCarrying — refresh 도 토큰을 회전하므로 (WEB 이면) 새 쿠키를 내리고 body 토큰을 비운다.
@Schema(description = "토큰 갱신 응답")
data class TokenRefreshResponse(
    @get:JsonIgnore
    override val tokenPair: TokenPair,
    @get:JsonIgnore
    val bodyTokensIncluded: Boolean = true,
) : TokenCarrying {
    @get:Schema(description = "새 액세스 토큰 (APP=값 / WEB=null, 쿠키로 전달)", nullable = true, example = "eyJhbGciOiJIUzI1NiJ9...")
    val accessToken: String? get() = tokenPair.accessToken.takeIf { bodyTokensIncluded }

    @get:Schema(description = "새 리프레시 토큰 (APP=값 / WEB=null, 쿠키로 전달)", nullable = true, example = "eyJhbGciOiJIUzI1NiJ9...")
    val refreshToken: String? get() = tokenPair.refreshToken.takeIf { bodyTokensIncluded }

    override fun withoutBodyTokens(): TokenCarrying = copy(bodyTokensIncluded = false)

    companion object {
        fun from(tokenPair: TokenPair): TokenRefreshResponse = TokenRefreshResponse(tokenPair = tokenPair)
    }
}
