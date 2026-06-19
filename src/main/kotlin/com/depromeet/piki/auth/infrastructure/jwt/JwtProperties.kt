package com.depromeet.piki.auth.infrastructure.jwt

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.time.DurationMax
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
    // refresh 토큰 회전 직후, 이전 토큰을 이 시간 동안 멱등 유효 처리하는 grace 창 (reuse interval).
    // Next.js App Router 등에서 한 페이지 진입에 동시 다발 요청이 같은 옛 토큰으로 refresh 를 호출할 때,
    // 한쪽만 회전 성공하고 나머지가 401 로 튕겨 로그아웃되는 race 를 흡수한다. 창 밖 재사용은 그대로 탐지.
    // 상·하한을 모두 강제한다. grace 는 재사용 탐지를 일시 완화하는 보안 동작이라, env 오설정으로 수분·수시간이
    // 들어오면 탈취 토큰 재사용 허용 구간이 비정상적으로 길어진다. 동시 요청 버스트 흡수엔 수초면 충분하므로 60초 상한.
    @field:DurationMin(seconds = 1, message = "refreshTokenGrace 는 1초 이상이어야 한다.")
    @field:DurationMax(seconds = 60, message = "refreshTokenGrace 는 60초 이하여야 한다 (재사용 허용 구간 과다 방지).")
    val refreshTokenGrace: Duration,
) {
    companion object {
        // HS256 의 키 최소 길이 (RFC 7518 §3.2: key MUST be at least 256 bits = 32 bytes).
        private const val MIN_SECRET_BYTES = 32
    }
}
