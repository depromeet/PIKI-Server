package com.depromeet.team3.auth.controller

import com.depromeet.team3.auth.controller.dto.DevUserCreateRequest
import com.depromeet.team3.auth.controller.dto.GuestCreateResponse
import com.depromeet.team3.common.response.ApiResponseBody
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType

@Tag(name = "Dev", description = "개발·테스트 전용 API (운영 환경 비활성화)")
interface DevAuthApi {
    @Operation(
        summary = "개발용 MEMBER 생성",
        description = "OAuth 없이 MEMBER User 를 생성하고 JWT 토큰 쌍을 발급한다. GUEST 토큰으로 호출해야 한다. dev/local 프로파일에서만 활성화된다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "MEMBER 생성 성공",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "닉네임 미입력 또는 형식 오류",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun createDevUser(request: DevUserCreateRequest): ApiResponseBody<GuestCreateResponse>
}
