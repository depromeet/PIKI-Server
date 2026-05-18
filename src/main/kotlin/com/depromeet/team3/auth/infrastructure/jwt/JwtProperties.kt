package com.depromeet.team3.auth.infrastructure.jwt

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("jwt")
data class JwtProperties(
    @field:NotBlank
    val secret: String,
    val accessTokenExpirySeconds: Long = 3600,
    val refreshTokenExpirySeconds: Long = 1_209_600,
)
