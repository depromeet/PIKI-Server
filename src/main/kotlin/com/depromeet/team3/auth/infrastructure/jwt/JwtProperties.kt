package com.depromeet.team3.auth.infrastructure.jwt

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("jwt")
data class JwtProperties(
    val secret: String = "piki-dev-secret-key-at-least-32-chars!",
    val accessTokenExpirySeconds: Long = 3600,
    val refreshTokenExpirySeconds: Long = 1_209_600,
)
