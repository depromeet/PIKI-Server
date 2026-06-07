package com.depromeet.piki.auth.infrastructure.oauth.google.dto

import com.depromeet.piki.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.piki.auth.infrastructure.oauth.OAuthUserInfo

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
