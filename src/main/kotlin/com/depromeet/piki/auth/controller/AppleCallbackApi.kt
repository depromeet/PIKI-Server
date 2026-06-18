package com.depromeet.piki.auth.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity

@Tag(name = "Auth", description = "인증 API")
interface AppleCallbackApi {
    @Operation(
        summary = "Apple OAuth form_post 콜백 브릿지 (웹)",
        description =
            "**Apple 이 직접 호출하는 웹 OAuth 콜백 엔드포인트다 (클라이언트 앱이 호출하지 않는다).**\n\n" +
                "Apple 은 `scope=email name` 이 있으면 `response_mode=form_post` 를 강제해, 콜백을 GET 쿼리가 아니라 " +
                "**POST(form_post)** 로 보낸다. Kakao·Google 은 GET 쿼리라 프론트 공용 콜백이 그대로 처리하지만, " +
                "Apple 만 POST 라 프론트가 받지 못한다. 그래서 이 엔드포인트가 Apple 의 POST 를 받아 `code`·`state` 를 " +
                "꺼내, 프론트 공용 콜백(`/auth/callback/apple?code=...&state=...`)으로 **302 리다이렉트**해 Kakao·Google 과 " +
                "동일한 GET 쿼리 흐름으로 통일한다.\n\n" +
                "**이 엔드포인트는 로그인을 처리하지 않는다.** 토큰 교환·로그인·쿠키 발급은 302 이후 프론트 공용 핸들러가 " +
                "기존 `POST /api/v1/auth/login/apple` 을 호출해 수행한다 (`state` 검증도 그쪽에서). 따라서 여기서는 `state` 를 " +
                "소비하지 않고 그대로 전달만 한다.\n\n" +
                "redirect_uri 로 이 엔드포인트가 Apple Developer Console 의 Return URLs 에 등록돼야 동작한다.",
    )
    // Apple 이 인증 없이 호출하는 진입점 (SecurityConfig 의 permitAll). 글로벌 Bearer 요구를 해제한다.
    @SecurityRequirements
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "302",
                description =
                    "프론트 공용 콜백으로 리다이렉트. 정상: `/auth/callback/apple?code=...&state=...`. " +
                        "Apple 이 `error` 를 보내면: `/auth/callback/apple?error=...` (프론트 공용 핸들러가 일관 처리).",
            ),
        ],
    )
    fun callback(
        @Parameter(description = "Apple 이 발급한 authorization code (form 필드)") code: String?,
        @Parameter(description = "CSRF 방지용 state (form 필드). 여기서 검증·소비하지 않고 프론트로 그대로 전달한다") state: String?,
        @Parameter(description = "Apple 이 인증 실패·취소 시 보내는 에러 코드 (예: user_cancelled)") error: String?,
    ): ResponseEntity<Void>
}
