package com.depromeet.piki.auth.web

import org.springframework.boot.context.properties.ConfigurationProperties

// 토큰 쿠키의 env-varying 한 속성만 둔다. 이름·Path·SameSite 등 환경 무관한 정책은
// TokenCookieWriter 의 상수로 고정한다.
//
// secure: 운영(HTTPS)은 true. 로컬(HTTP)은 false — Secure 쿠키는 HTTPS 에서만 전송돼
// 로컬에서 쿠키 흐름 테스트가 막히기 때문이다. 누락 시 기본 true(fail-safe-secure)로 두고,
// 로컬은 .env 의 COOKIE_SECURE=false 로 끈다.
@ConfigurationProperties("auth.cookie")
data class AuthCookieProperties(
    val secure: Boolean = true,
)
