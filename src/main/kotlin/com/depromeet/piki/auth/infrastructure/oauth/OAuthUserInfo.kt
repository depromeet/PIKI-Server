package com.depromeet.piki.auth.infrastructure.oauth

data class OAuthUserInfo(
    val provider: OAuthProvider,
    val socialId: String,
    // provider 가 프로필 이미지를 안 주거나(사용자 동의 거부 등) 빈 값이면 null.
    // 소비측(소셜 가입)에서 null 이면 기본 이미지로 대체한다. email 은 받지 않기로 결정(스키마에서도 드롭).
    val profileImage: String?,
)
