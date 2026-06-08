package com.depromeet.piki.auth.infrastructure.oauth.google.dto

import com.depromeet.piki.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.piki.auth.infrastructure.oauth.OAuthUserInfo
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// Google userinfo 응답 wire 모델. 우리가 쓰는 필드(id·picture·email)만 모델링하고,
// 나머지(verified_email·name·locale 등)는 ignoreUnknown 으로 무시한다 — 운영 ObjectMapper 설정에
// 의존하지 않고 DTO 자체가 부분 매핑을 명시한다(KakaoUserInfoResponse·GeminiGenerateContentResponse 와 동일).
@JsonIgnoreProperties(ignoreUnknown = true)
data class GoogleUserInfoResponse(
    val id: String,
    val picture: String = "",
    // scope 에 email 이 있으나 사용자가 미동의하면 응답에 빠질 수 있어 nullable.
    val email: String? = null,
) {
    // 외부 응답 → 도메인 매핑 (CLAUDE.md: 외부 결과 객체의 toXxx()). picture·email 은 빈 문자열이면 null 로 정규화.
    fun toOAuthUserInfo(): OAuthUserInfo =
        OAuthUserInfo(
            provider = OAuthProvider.GOOGLE,
            socialId = id,
            profileImage = picture.ifBlank { null },
            email = email?.ifBlank { null },
        )
}
