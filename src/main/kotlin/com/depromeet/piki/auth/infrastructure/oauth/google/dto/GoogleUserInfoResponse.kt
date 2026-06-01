package com.depromeet.piki.auth.infrastructure.oauth.google.dto

data class GoogleUserInfoResponse(
    val id: String,
    val picture: String = "",
)
