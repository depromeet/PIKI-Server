package com.depromeet.piki.auth.controller.dto

import com.depromeet.piki.auth.service.dto.OAuthLoginCommand
import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue

@Schema(description = "소셜 로그인 요청 — v1(웹): code+redirectUri / v2(SDK): accessToken")
data class OAuthLoginRequest(
    @field:Schema(description = "v1 웹 흐름 — provider 가 redirect 로 준 인가 코드", nullable = true)
    val code: String? = null,
    @field:Schema(description = "v1 웹 흐름 — 코드 발급에 사용한 redirect_uri (provider 에 등록된 값과 일치)", nullable = true)
    val redirectUri: String? = null,
    @field:Schema(description = "v2 SDK 흐름 — 모바일 SDK 가 받은 access_token", nullable = true)
    val accessToken: String? = null,
    @field:Schema(
        description = "CSRF 방지용 state. GET /auth/{provider}/url 로 발급받은 값을 전송 (v1 웹 흐름 권장·v2 선택). 미전송 시 state 검증을 생략한다.",
        nullable = true,
    )
    val state: String? = null,
) {
    // v1(code+redirectUri) XOR v2(accessToken) — 정확히 한 흐름만 유효. 둘 다·둘 다 없음·공백은 400(입력 경계 계약).
    @get:JsonIgnore
    @get:AssertTrue(message = VALID_FLOW_MESSAGE)
    val validFlow: Boolean
        get() {
            val v1 = !code.isNullOrBlank() && !redirectUri.isNullOrBlank()
            val v2 = !accessToken.isNullOrBlank()
            return v1 xor v2
        }

    fun toCommand(): OAuthLoginCommand =
        OAuthLoginCommand(code = code, redirectUri = redirectUri, accessToken = accessToken, state = state)

    companion object {
        // validFlow 위반(code+redirectUri 와 accessToken 을 둘 다·둘 다 없이 보냄)은 엔드유저 행동이 아니라
        // 앱이 흐름을 잘못 구성한 클라이언트 구현 버그다. 정상 앱은 항상 한 흐름만 보내므로 사용자는 닿지 않는다.
        // 따라서 detail 은 사용자 친화 문구가 아니라 앱 개발자가 디버깅할 구체 메시지로 둔다.
        // Bean Validation 위반 메시지의 single source — OpenApiExamples 가 같은 상수를 참조한다.
        const val VALID_FLOW_MESSAGE = "code+redirectUri 또는 accessToken 중 한 흐름만 보내야 합니다."
    }
}
