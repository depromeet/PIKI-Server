package com.depromeet.team3.auth.infrastructure.jwt

import org.springframework.boot.context.properties.ConfigurationProperties

// secret 은 환경변수에서만 주입한다. default 값을 두면 운영에서 env 가 누락됐을 때
// 공개된 키로 토큰을 서명하게 되어 위조 가능성이 생긴다.
@ConfigurationProperties("jwt")
data class JwtProperties(
    val secret: String,
    val accessTokenExpirySeconds: Long,
    val refreshTokenExpirySeconds: Long,
)
