package com.depromeet.piki.auth.controller

import com.depromeet.piki.auth.controller.dto.OAuthUrlResponse
import com.depromeet.piki.common.response.ApiResponseBody
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType

@Tag(name = "Auth", description = "인증 API")
interface OAuthUrlApi {
    @Operation(
        summary = "OAuth 인가 URL 생성",
        description =
            "provider 인가 페이지 URL 과 CSRF 방지용 state 를 반환한다. " +
                "FE 는 이 URL 로 사용자를 redirect 하고, provider 콜백에서 받은 state 가 응답의 state 와 일치하는지 검증한 뒤 " +
                "POST /auth/login/{provider} 에 code · redirectUri · state 를 전송한다.",
    )
    @SecurityRequirements
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "인가 URL + state 생성 성공",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (지원하지 않는 provider · 허용되지 않은 redirect_uri)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun getAuthUrl(
        @Parameter(description = "소셜 제공자", example = "kakao", schema = Schema(allowableValues = ["kakao", "google"]))
        provider: String,
        @Parameter(
            description = "redirect_uri 동적 지정 (생략 시 서버 기본값 사용). 로컬 개발 등 프로덕션과 다른 콜백 URL 이 필요할 때 사용.",
            example = "http://localhost:3000/auth/callback/google",
        )
        redirectUri: String?,
    ): ApiResponseBody<OAuthUrlResponse>
}
