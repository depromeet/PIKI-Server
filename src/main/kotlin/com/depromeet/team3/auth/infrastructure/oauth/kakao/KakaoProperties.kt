package com.depromeet.team3.auth.infrastructure.oauth.kakao

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("oauth.kakao")
data class KakaoProperties(
    val clientId: String = "",
    val clientSecret: String = "",
    val redirectUri: String = "",
)
