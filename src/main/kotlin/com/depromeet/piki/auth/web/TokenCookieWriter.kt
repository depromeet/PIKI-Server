package com.depromeet.piki.auth.web

import com.depromeet.piki.auth.infrastructure.jwt.JwtProperties
import com.depromeet.piki.auth.service.dto.TokenPair
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component

// 토큰 쿠키 정책(이름·HttpOnly·Secure·SameSite·Path·Max-Age)을 한 곳에 캡슐화한다.
// 컨트롤러·서비스·토큰 도메인은 이 정책을 모른다 — advice 만 이 빈을 사용한다.
//
// - access_token: Path=/ (모든 API 요청에 전송돼 인증)
// - refresh_token: Path=/api/v1/auth (refresh·logout 에만 전송 → 노출 최소화)
// - Domain 미설정(host-only): 쿠키는 api 호스트만 소비하므로 서브도메인 공유 불필요
// - SameSite=Strict: same-site 전제라 SPA fetch 는 전송되고 third-party CSRF 는 차단
@Component
class TokenCookieWriter(
    private val jwtProperties: JwtProperties,
    private val authCookieProperties: AuthCookieProperties,
) {
    fun setCookies(tokenPair: TokenPair): List<ResponseCookie> =
        listOf(
            build(ACCESS_COOKIE, tokenPair.accessToken, ROOT_PATH, jwtProperties.accessTokenExpiry.seconds),
            build(REFRESH_COOKIE, tokenPair.refreshToken, AUTH_PATH, jwtProperties.refreshTokenExpiry.seconds),
        )

    // 만료(삭제) 쿠키: 동일 name·path·속성 + 빈 값 + Max-Age=0.
    // path 가 set 시점과 같아야 브라우저가 해당 쿠키를 삭제한다.
    fun clearCookies(): List<ResponseCookie> =
        listOf(
            build(ACCESS_COOKIE, "", ROOT_PATH, 0),
            build(REFRESH_COOKIE, "", AUTH_PATH, 0),
        )

    private fun build(
        name: String,
        value: String,
        path: String,
        maxAgeSeconds: Long,
    ): ResponseCookie =
        ResponseCookie
            .from(name, value)
            .httpOnly(true)
            .secure(authCookieProperties.secure)
            .sameSite(SAME_SITE)
            .path(path)
            .maxAge(maxAgeSeconds)
            .build()

    companion object {
        const val ACCESS_COOKIE = "access_token"
        const val REFRESH_COOKIE = "refresh_token"
        private const val ROOT_PATH = "/"
        private const val AUTH_PATH = "/api/v1/auth"
        private const val SAME_SITE = "Strict"
    }
}
