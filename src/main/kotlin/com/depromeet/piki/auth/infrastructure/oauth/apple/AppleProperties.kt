package com.depromeet.piki.auth.infrastructure.oauth.apple

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

// env(.env / 운영 환경변수)에서만 주입한다. @Validated + @NotBlank 로 부팅 시점에 BindException 으로 fail-fast.
@Validated
@ConfigurationProperties("oauth.apple")
data class AppleProperties(
    @field:NotBlank(message = "Apple team-id 는 비어 있을 수 없다.")
    val teamId: String,
    @field:NotBlank(message = "Apple key-id 는 비어 있을 수 없다.")
    val keyId: String,
    // Services ID — v1(서버 redirect) 흐름의 client_id, id_token 의 aud 값
    @field:NotBlank(message = "Apple client-id 는 비어 있을 수 없다.")
    val clientId: String,
    // Bundle ID — iOS SDK(v2) 가 발급한 identityToken 의 aud 값
    @field:NotBlank(message = "Apple bundle-id 는 비어 있을 수 없다.")
    val bundleId: String,
    // .p8 파일 내용. 환경변수로 주입 시 개행을 \n 으로 치환해 한 줄로 넣는다.
    @field:NotBlank(message = "Apple private-key 는 비어 있을 수 없다.")
    val privateKey: String,
    @field:NotBlank(message = "Apple redirect-uri 는 비어 있을 수 없다.")
    val redirectUri: String,
)
