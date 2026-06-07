package com.depromeet.piki.auth.infrastructure.oauth.google.dto

data class GoogleUserInfoResponse(
    val id: String,
    val picture: String = "",
    // scope 에 email 이 있으나 사용자가 미동의하면 응답에 빠질 수 있어 nullable.
    val email: String? = null,
)
