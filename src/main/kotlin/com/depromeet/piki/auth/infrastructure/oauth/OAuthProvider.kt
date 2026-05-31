package com.depromeet.piki.auth.infrastructure.oauth

// V6 마이그레이션 CHECK 제약과 에픽 #122 Task 3 항목 모두 APPLE 을 포함한다.
// Apple OAuth 클라이언트 구현은 후속 작업 (Sign in with Apple 의 client_secret JWT 갱신 흐름이
// Kakao/Google 과 달라 별도 비중) 이라 enum 만 미리 추가해 두고 구현체는 추후 도입한다.
enum class OAuthProvider {
    KAKAO,
    GOOGLE,
    APPLE,
    ;

    companion object {
        // path 의 provider 문자열을 enum 으로. 미상은 400 (구현체 유무는 registry 가 판단).
        fun from(raw: String): OAuthProvider =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: throw OAuthException.unsupportedProvider()
    }
}
