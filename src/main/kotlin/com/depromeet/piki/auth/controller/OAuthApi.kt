package com.depromeet.piki.auth.controller

import com.depromeet.piki.auth.controller.dto.OAuthLoginRequest
import com.depromeet.piki.auth.controller.dto.OAuthLoginResponse
import com.depromeet.piki.auth.web.ClientType
import com.depromeet.piki.common.response.ApiResponseBody
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import java.util.UUID

@Tag(name = "Auth", description = "인증 API")
interface OAuthApi {
    @Operation(
        summary = "소셜 로그인",
        description =
            "kakao/google 소셜 로그인. v1(웹)은 body 에 code+redirectUri, v2(SDK)는 body 에 accessToken 을 보낸다. " +
                "처음 보는 소셜이면 MEMBER 로 가입(닉네임 자동 fill, 이후 수정 가능)하고, 기존이면 로그인한다. " +
                "게스트 토큰을 함께 보내면 그 게스트 계정에 소셜을 연결+승격해 위시·토너먼트 데이터를 이어준다. " +
                "응답 토큰은 기본 HttpOnly 쿠키로 내려가며, X-Client-Type: app 일 때만 body 로 내린다. " +
                "v1 웹 흐름은 GET /auth/{provider}/url 로 발급받은 state 를 body 에 함께 전송하면 CSRF 검증이 활성화된다.",
    )
    // 만료/미인증 클라이언트도 호출하므로 인증 없이 진입 (SecurityConfig 의 permitAll).
    @SecurityRequirements
    @Parameter(
        name = ClientType.HEADER,
        `in` = ParameterIn.HEADER,
        required = false,
        description = "클라이언트 종류. app 이면 body 로 토큰을 받는다. 그 외·미설정은 HttpOnly 쿠키(기본).",
        schema = Schema(allowableValues = ["web", "app"]),
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "로그인/가입 성공 (기본: Set-Cookie + body 토큰 null / app: body 토큰)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description =
                    "잘못된 요청 (code+redirectUri 도 accessToken 도 없음 · accessToken 과 code 를 동시 전달 · 지원하지 않는 provider · " +
                        "provider 인가코드(code) 가 만료/재사용/무효 — 재로그인으로 새 code 를 받아 재시도)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description =
                    "인증 실패 (state 검증 실패: 만료·미발급·이미 소비된 state — GET /auth/{provider}/url 로 재발급 필요 · " +
                        "provider access token 무효/만료 — 재로그인으로 새 토큰을 받아 재시도)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "502",
                description =
                    "소셜 제공자 호출 경계 실패 (응답 body 의 category 로 구분) — " +
                        "(1) provider 장애(네트워크·5xx·점검·user_info 조회 실패·미지 에러코드 fallback): RETRYABLE, 동일 요청으로 재시도 가능 / " +
                        "(2) 우리 OAuth 설정·요청 오류(client_id/secret·client_secret JWT·필수 인자 누락 등): SERVER_ERROR, 재시도 무의미·서버 설정 수정 필요",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun login(
        @Parameter(description = "소셜 제공자", example = "kakao", schema = Schema(allowableValues = ["kakao", "google"]))
        provider: String,
        request: OAuthLoginRequest,
        @Parameter(hidden = true) currentUserId: UUID?,
    ): ApiResponseBody<OAuthLoginResponse>
}
