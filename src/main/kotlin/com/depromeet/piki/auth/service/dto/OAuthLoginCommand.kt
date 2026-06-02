package com.depromeet.piki.auth.service.dto

// 소셜 로그인 자격 증명. v1(web): code+redirectUri / v2(SDK): accessToken.
// 둘 중 하나만 채워져 오며, 서비스가 accessToken 우선으로 흐름을 고른다.
// state 는 CSRF 방지용 (GET /auth/{provider}/url 에서 발급). null 이면 검증 생략.
data class OAuthLoginCommand(
    val code: String?,
    val redirectUri: String?,
    val accessToken: String?,
    val state: String? = null,
)
