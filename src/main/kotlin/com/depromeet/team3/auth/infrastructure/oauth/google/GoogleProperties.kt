package com.depromeet.team3.auth.infrastructure.oauth.google

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("oauth.google")
data class GoogleProperties(
    val clientId: String = "",
    val clientSecret: String = "",
    val redirectUri: String = "",
)
