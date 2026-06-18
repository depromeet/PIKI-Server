package com.depromeet.piki.auth.infrastructure.oauth.apple

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

// env(.env / 운영 환경변수)에서만 주입한다. @Validated + @NotBlank 로 부팅 시점에 BindException 으로 fail-fast.
@Validated
@ConfigurationProperties("oauth.apple")
data class AppleProperties(
    @field:NotBlank(message = "Apple Team ID 가 필요합니다 (env: APPLE_TEAM_ID / property: oauth.apple.team-id).")
    val teamId: String,
    @field:NotBlank(message = "Apple Key ID 가 필요합니다 (env: APPLE_KEY_ID / property: oauth.apple.key-id).")
    val keyId: String,
    // Services ID — v1(서버 redirect) 흐름의 client_id, id_token 의 aud 값
    @field:NotBlank(message = "Apple Services ID 가 필요합니다 (env: APPLE_CLIENT_ID / property: oauth.apple.client-id).")
    val clientId: String,
    // Bundle ID — iOS SDK(v2) 가 발급한 identityToken 의 aud 값
    @field:NotBlank(message = "Apple Bundle ID 가 필요합니다 (env: APPLE_BUNDLE_ID / property: oauth.apple.bundle-id).")
    val bundleId: String,
    // .p8 파일 내용. 환경변수로 주입 시 개행을 \n 으로 치환해 한 줄로 넣는다.
    @field:NotBlank(message = "Apple Private Key 가 필요합니다 (env: APPLE_PRIVATE_KEY / property: oauth.apple.private-key).")
    val privateKey: String,
    // Apple 웹 OAuth 의 redirect_uri. v1(웹) 흐름에서 Apple 이 form_post 콜백을 보낼 주소이자 token 교환의 redirect_uri.
    // 웹 form_post 브릿지(#430) 도입 후엔 이 값이 BE 브릿지 엔드포인트(/api/v1/auth/apple/callback)를 가리킨다.
    @field:NotBlank(message = "Apple Redirect URI 가 필요합니다 (env: APPLE_REDIRECT_URI / property: oauth.apple.redirect-uri).")
    val redirectUri: String,
    // Apple form_post 브릿지(#430)가 code·state 를 붙여 302 시킬 프론트 공용 콜백 URL (예: https://dev.piki.day/auth/callback/apple).
    // redirectUri(BE 브릿지)와 짝 — Apple 의 POST 를 받아 이 주소로 GET 쿼리 리다이렉트해 Kakao·Google 과 흐름을 통일한다.
    // 브릿지가 UriComponentsBuilder.fromUriString 으로 302 Location 을 만들므로, 절대 URL(http/https)이어야 한다.
    // @NotBlank 만으론 상대경로·스킴 오타가 부팅을 통과해 요청 시점에 깨지므로, 절대 URL 검증을 부팅 fail-fast 로 둔다.
    @field:NotBlank(message = "Apple Web Callback URL 이 필요합니다 (env: APPLE_WEB_CALLBACK_URL / property: oauth.apple.web-callback-url).")
    @field:Pattern(
        regexp = "^https?://.+",
        message = "Apple Web Callback URL 은 절대 URL(http:// 또는 https://)이어야 합니다 (env: APPLE_WEB_CALLBACK_URL).",
    )
    val webCallbackUrl: String,
) {
    // data class 의 자동 toString() 은 .p8 평문을 그대로 노출시킨다. 부팅 로그·BindException·디버그
    // 로그에 새지 않도록 오버라이드해 privateKey 만 마스킹한다 (equals/hashCode 는 data class 기본 유지).
    override fun toString(): String =
        "AppleProperties(teamId=$teamId, keyId=$keyId, clientId=$clientId, bundleId=$bundleId, " +
            "privateKey=***MASKED***, redirectUri=$redirectUri, webCallbackUrl=$webCallbackUrl)"
}
