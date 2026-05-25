package com.depromeet.piki.auth.infrastructure.oauth

data class OAuthUserInfo(
    val provider: OAuthProvider,
    val socialId: String,
    val email: String,
    val profileImage: String,
)
