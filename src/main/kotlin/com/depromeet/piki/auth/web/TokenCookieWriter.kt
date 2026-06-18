package com.depromeet.piki.auth.web

import com.depromeet.piki.auth.infrastructure.jwt.JwtProperties
import com.depromeet.piki.auth.service.dto.TokenPair
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component

// 토큰 쿠키 정책(이름·HttpOnly·Secure·SameSite·Path·Max-Age)을 한 곳에 캡슐화한다.
// 컨트롤러·서비스·토큰 도메인은 이 정책을 모른다 — advice 만 이 빈을 사용한다.
//
// - access_token: Path=/ (모든 API 요청에 전송돼 인증)
// - refresh_token: Path=/ (#512) — 웹 Next.js proxy(엣지 미들웨어)가 페이지 네비게이션(/archive·/home)에서
//   refresh_token 유무를 읽어 transparent refresh 를 판단해야 한다. /api/v1/auth 로 좁히면 일반 페이지
//   요청엔 쿠키가 안 실려 proxy 의 refresh 분기가 죽는다. Path 좁히기는 defense-in-depth 였을 뿐, 실제
//   보호는 HttpOnly·SameSite=Lax·Secure 가 하며 이 셋은 그대로 둔다. 넓힘으로 늘어나는 노출은 "모든
//   same-origin 요청에 refresh_token 이 실려 로그 Cookie 헤더에 찍힘" 한 곳뿐이라 로그 마스킹으로 상쇄(#497).
// - 전환기 cleanup: 배포 전 발급된 옛 refresh_token 은 Path=/api/v1/auth 로 남는다. 새 Path=/ 쿠키와
//   공존하면 /api/v1/auth/token/refresh 요청에 둘 다 실리고, RFC 6265 상 더 구체적인 옛 경로 쿠키가 먼저
//   선택된다. refresh 는 회전식(store 의 최신 토큰과 일치해야 통과)이라 옛 토큰은 불일치로 거부돼 갱신이
//   실패한다. 그래서 토큰을 새로 내리거나(setCookies) 만료시킬(clearCookies) 때마다 옛 경로 쿠키를 함께
//   Max-Age=0 으로 지운다. 활성 refresh TTL 이 지나 옛 쿠키가 전부 사라지면 이 cleanup 은 제거 가능.
// - Domain 미설정(host-only): 쿠키는 api 호스트만 소비하므로 서브도메인 공유 불필요
// - SameSite=Lax: OAuth provider 콜백처럼 cross-site 리다이렉트로 우리 사이트에 도착한 직후의 top-level
//   네비게이션에도 쿠키가 실려야 로그인이 끊기지 않는다. Strict 는 그 첫 요청에 안 붙어 OAuth 후 로그아웃처럼
//   보였다(provider 마다 FE 가 따로 패치하던 문제의 근원). Lax 도 state-changing(POST·PUT·DELETE) cross-site
//   요청엔 쿠키를 안 보내 third-party CSRF 는 그대로 차단한다. FE·BE 가 same-site(piki.day)라 None 은 불필요.
@Component
class TokenCookieWriter(
    private val jwtProperties: JwtProperties,
    private val authCookieProperties: AuthCookieProperties,
) {
    fun setCookies(tokenPair: TokenPair): List<ResponseCookie> =
        listOf(
            build(ACCESS_COOKIE, tokenPair.accessToken, ROOT_PATH, jwtProperties.accessTokenExpiry.seconds),
            build(REFRESH_COOKIE, tokenPair.refreshToken, ROOT_PATH, jwtProperties.refreshTokenExpiry.seconds),
            expireLegacyRefreshCookie(),
        )

    // 만료(삭제) 쿠키: 동일 name·path·속성 + 빈 값 + Max-Age=0.
    // path 가 set 시점과 같아야 브라우저가 해당 쿠키를 삭제한다.
    fun clearCookies(): List<ResponseCookie> =
        listOf(
            build(ACCESS_COOKIE, "", ROOT_PATH, 0),
            build(REFRESH_COOKIE, "", ROOT_PATH, 0),
            expireLegacyRefreshCookie(),
        )

    // 전환기 cleanup (클래스 주석 참조, #512): 옛 Path=/api/v1/auth refresh_token 쿠키를 만료시켜
    // 새 Path=/ 쿠키와의 공존(→ 회전 토큰 불일치로 갱신 실패)을 막는다. 옛 쿠키가 전부 만료되면 제거 가능.
    private fun expireLegacyRefreshCookie(): ResponseCookie = build(REFRESH_COOKIE, "", LEGACY_REFRESH_PATH, 0)

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
        private const val LEGACY_REFRESH_PATH = "/api/v1/auth" // 전환기 cleanup 전용 (#512), 옛 쿠키 만료 후 제거 가능
        private const val SAME_SITE = "Lax"
    }
}
