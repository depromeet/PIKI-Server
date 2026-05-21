package com.depromeet.team3.auth.infrastructure.jwt

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.time.DurationMin
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

// secret 은 환경변수에서만 주입한다. default 값을 두면 운영에서 env 가 누락됐을 때
// 공개된 키로 토큰을 서명하게 되어 위조 가능성이 생긴다.
//
// @Validated + 필드 제약으로 부팅 시점에 BindException 으로 fail-fast.
// 잘못된 env (짧은 secret / 0 이하 만료 시간) 가 들어와도 트래픽 시점이 아니라
// 부팅 시점에 즉시 깨지도록 한다.
@Validated
@ConfigurationProperties("jwt")
data class JwtProperties(
    @field:NotBlank(message = "JWT secret 은 비어 있거나 공백만으로 구성될 수 없다.")
    @field:Size(min = MIN_SECRET_BYTES, message = "JWT secret 은 $MIN_SECRET_BYTES bytes 이상이어야 한다 (HS256 최소).")
    val secret: String,
    @field:DurationMin(seconds = 1, message = "accessTokenExpiry 는 1초 이상이어야 한다.")
    val accessTokenExpiry: Duration,
    @field:DurationMin(seconds = 1, message = "refreshTokenExpiry 는 1초 이상이어야 한다.")
    val refreshTokenExpiry: Duration,
) {
    companion object {
        // HS256 의 키 최소 길이 (RFC 7518 §3.2: key MUST be at least 256 bits = 32 bytes).
        private const val MIN_SECRET_BYTES = 32
    }
}
