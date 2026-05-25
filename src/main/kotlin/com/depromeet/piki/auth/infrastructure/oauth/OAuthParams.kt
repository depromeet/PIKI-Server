package com.depromeet.piki.auth.infrastructure.oauth

// OAuth 2.0 (RFC 6749) 표준 form 파라미터 + grant type. Spring Security 의
// OAuth2ParameterNames 와 같은 값이지만 spring-security-oauth2-core 의존성 도입을
// 피하기 위해 자체 상수로 보관한다. provider 별 client 가 공통으로 참조한다.
internal object OAuthParams {
    const val GRANT_TYPE = "grant_type"
    const val CLIENT_ID = "client_id"
    const val CLIENT_SECRET = "client_secret"
    const val REDIRECT_URI = "redirect_uri"
    const val CODE = "code"
    const val GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code"
}
