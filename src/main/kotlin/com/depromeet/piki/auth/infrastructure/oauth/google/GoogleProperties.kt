package com.depromeet.piki.auth.infrastructure.oauth.google

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

// env(.env / 운영 환경변수)에서만 주입한다. 빈 default 를 두면 env 누락 시 빈 문자열로 조용히 떠
// 첫 로그인 호출에서야 깨진다. @Validated + @NotBlank 로 부팅 시점에 BindException 으로 fail-fast.
@Validated
@ConfigurationProperties("oauth.google")
data class GoogleProperties(
    @field:NotBlank(message = "Google client-id 는 비어 있을 수 없다.")
    val clientId: String,
    @field:NotBlank(message = "Google client-secret 는 비어 있을 수 없다.")
    val clientSecret: String,
    @field:NotBlank(message = "Google redirect-uri 는 비어 있을 수 없다.")
    val redirectUri: String,
    // 추가 허용 redirect_uri. 로컬 개발 등에서 필요시 .env.local 에 GOOGLE_ALLOWED_REDIRECT_URIS[0]=http://localhost:... 로 주입.
    val allowedRedirectUris: List<String> = emptyList(),
)
