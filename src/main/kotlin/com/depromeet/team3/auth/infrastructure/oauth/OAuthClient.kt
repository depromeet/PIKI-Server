package com.depromeet.team3.auth.infrastructure.oauth

interface OAuthClient {
    val provider: OAuthProvider

    fun fetchUserInfo(
        code: String,
        redirectUri: String,
    ): OAuthUserInfo
}
