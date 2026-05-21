package com.depromeet.team3.auth.infrastructure.oauth.kakao.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class KakaoTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
)
