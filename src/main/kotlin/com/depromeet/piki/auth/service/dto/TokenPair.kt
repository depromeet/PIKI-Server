package com.depromeet.piki.auth.service.dto

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
) {
    // raw 토큰이 로그·예외에 새지 않도록 toString 을 마스킹한다. data class 자동 toString 이
    // 토큰을 그대로 노출하는 것을 막고, 이 객체를 주 생성자에 포함하는 DTO
    // (GuestCreateResponse·TokenRefreshResponse)의 자동 toString 도 함께 보호된다.
    override fun toString(): String = "TokenPair(accessToken=***, refreshToken=***)"
}
