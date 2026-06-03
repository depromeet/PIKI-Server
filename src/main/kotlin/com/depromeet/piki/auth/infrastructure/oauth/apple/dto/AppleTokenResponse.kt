package com.depromeet.piki.auth.infrastructure.oauth.apple.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class AppleTokenResponse(
    @JsonProperty("id_token") val idToken: String,
    @JsonProperty("access_token") val accessToken: String?,
    @JsonProperty("refresh_token") val refreshToken: String?,
    @JsonProperty("token_type") val tokenType: String?,
    @JsonProperty("expires_in") val expiresIn: Long?,
)
