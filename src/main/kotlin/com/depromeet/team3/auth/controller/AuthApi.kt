package com.depromeet.team3.auth.controller

import com.depromeet.team3.auth.controller.dto.GuestCreateResponse
import com.depromeet.team3.auth.controller.dto.TokenRefreshRequest
import com.depromeet.team3.auth.controller.dto.TokenRefreshResponse
import com.depromeet.team3.common.response.ApiResponseBody
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import java.util.UUID

@Tag(name = "Auth", description = "인증 API")
interface AuthApi {
    @Operation(
        summary = "게스트 생성",
        description = "새 GUEST User 를 생성하고 JWT 토큰 쌍을 발급한다.",
    )
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
        ],
    )
    fun createGuest(): ApiResponseBody<GuestCreateResponse>

    @Operation(
        summary = "토큰 갱신",
        description = "리프레시 토큰을 검증하고 새 액세스·리프레시 토큰 쌍을 발급한다.",
    )
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
                responseCode = "401",
                description = "유효하지 않은 리프레시 토큰",
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
                description = "인증 필요",
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
