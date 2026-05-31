package com.depromeet.piki.auth.controller.dto

import com.depromeet.piki.auth.service.dto.OAuthLoginCommand
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "소셜 로그인 요청 — v1(웹): code+redirectUri / v2(SDK): accessToken")
data class OAuthLoginRequest(
    @field:Schema(description = "v1 웹 흐름 — provider 가 redirect 로 준 인가 코드", nullable = true)
    val code: String? = null,
    @field:Schema(description = "v1 웹 흐름 — 코드 발급에 사용한 redirect_uri (provider 에 등록된 값과 일치)", nullable = true)
    val redirectUri: String? = null,
    @field:Schema(description = "v2 SDK 흐름 — 모바일 SDK 가 받은 access_token", nullable = true)
    val accessToken: String? = null,
) {
    fun toCommand(): OAuthLoginCommand = OAuthLoginCommand(code = code, redirectUri = redirectUri, accessToken = accessToken)
}
