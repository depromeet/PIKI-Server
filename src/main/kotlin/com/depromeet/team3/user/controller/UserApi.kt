package com.depromeet.team3.user.controller

import com.depromeet.team3.common.response.ApiResponseBody
import com.depromeet.team3.user.controller.dto.NicknameCheckRequest
import com.depromeet.team3.user.controller.dto.NicknameCheckResponse
import com.depromeet.team3.user.controller.dto.UserResponse
import com.depromeet.team3.user.controller.dto.UserUpdateRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import java.util.UUID

@Tag(name = "User", description = "유저 API")
interface UserApi {
    @Operation(
        summary = "내 정보 조회",
        description = "현재 로그인된 유저(GUEST 포함) 의 정보를 조회한다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
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
    fun getMe(
        @Parameter(hidden = true) userId: UUID,
    ): ApiResponseBody<UserResponse>

    @Operation(
        summary = "내 정보 수정",
        description =
            "내 정보를 부분 수정한다. 현재는 nickname 만 수정 가능 — GUEST 도 호출할 수 있다. " +
                "회원 전용 필드는 추후 같은 PATCH 에 추가되며 권한 분기는 service 가 처리한다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "수정 성공",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "닉네임 길이/공백 검증 실패",
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
            ApiResponse(
                responseCode = "409",
                description = "닉네임 중복",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun updateMe(
        @Parameter(hidden = true) userId: UUID,
        request: UserUpdateRequest,
    ): ApiResponseBody<UserResponse>

    @Operation(
        summary = "닉네임 중복 체크",
        description = "닉네임이 이미 다른 유저에게 점유됐는지 확인한다. 회원 전환 / 닉네임 수정 전 사전 확인용.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "확인 성공",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "닉네임 형식 검증 실패",
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
    fun checkNickname(request: NicknameCheckRequest): ApiResponseBody<NicknameCheckResponse>
}
