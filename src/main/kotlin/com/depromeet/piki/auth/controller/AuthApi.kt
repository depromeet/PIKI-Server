package com.depromeet.piki.auth.controller

import com.depromeet.piki.auth.controller.dto.GuestCreateResponse
import com.depromeet.piki.auth.controller.dto.TokenRefreshRequest
import com.depromeet.piki.auth.controller.dto.TokenRefreshResponse
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
import java.util.UUID

@Tag(name = "Auth", description = "인증 API")
interface AuthApi {
    @Operation(
        summary = "게스트 생성",
        description = "새 GUEST User 를 생성하고 JWT 토큰 쌍을 발급한다.",
    )
    // 인증 없이 호출하는 진입점 (SecurityConfig 의 permitAll). 글로벌 Bearer 요구를 해제한다.
    @SecurityRequirements
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "게스트 생성 성공",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "500",
                description = "서버 오류 (닉네임 자동 생성 10회 시도 후 전부 중복 — 매우 드문 경우)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun createGuest(): ApiResponseBody<GuestCreateResponse>

    @Operation(
        summary = "토큰 갱신",
        description = "리프레시 토큰을 검증하고 새 액세스·리프레시 토큰 쌍을 발급한다.",
    )
    // 만료된 액세스 토큰을 가진 클라이언트도 호출해야 하므로 인증 없이 진입 (SecurityConfig 의 permitAll).
    @SecurityRequirements
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "토큰 갱신 성공",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "refreshToken 미입력",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "유효하지 않은 리프레시 토큰 (파싱 불가 · 만료 · Redis 값 불일치 · 탈퇴 유저)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun refresh(request: TokenRefreshRequest): ApiResponseBody<TokenRefreshResponse>

    @Operation(
        summary = "로그아웃",
        description = "리프레시 토큰을 삭제하여 로그아웃 처리한다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "로그아웃 성공",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "미인증 (JWT 토큰 없음 또는 유효하지 않음)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun logout(
        @Parameter(hidden = true) userId: UUID,
    ): ApiResponseBody<Unit>
}
