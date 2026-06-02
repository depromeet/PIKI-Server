package com.depromeet.piki.auth.infrastructure.oauth

// 클라이언트팀이 SDK 사용 결정 → 백엔드는 v1 + v2 두 흐름 모두 지원한다.
// - v1: 웹/외부 redirect 흐름. 백엔드가 code → access_token 교환을 직접 수행
// - v2: 클라이언트 SDK 흐름. SDK 가 받은 access_token 을 백엔드가 받아 user_info 만 조회
// Provider 별 구현 사유는 PR 본문 참조 (Kakao 재활용 / Google 분리 / Apple 후순위).
interface OAuthClient {
    val provider: OAuthProvider

    // RFC 6749 §4.1 Authorization Code Grant — provider 인가 URL 생성.
    // state 는 CSRF 방지용 (RFC 6749 §10.12). 호출자가 Redis 에 저장 후 사용자를 이 URL 로 redirect.
    fun buildAuthUrl(state: String): String

    fun fetchUserInfoByCode(
        code: String,
        redirectUri: String,
    ): OAuthUserInfo

    fun fetchUserInfoByAccessToken(accessToken: String): OAuthUserInfo
}
