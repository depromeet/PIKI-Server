package com.depromeet.piki.user.controller

import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.user.controller.dto.NicknameCheckRequest
import com.depromeet.piki.user.controller.dto.NicknameCheckResponse
import com.depromeet.piki.user.controller.dto.UserResponse
import com.depromeet.piki.user.controller.dto.UserUpdateRequest
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
                description = "미인증 (JWT 토큰 없음 또는 유효하지 않음)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "유저를 찾을 수 없음 (JWT 유효하지만 DB에서 유저가 삭제된 경우)",
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
                description = "닉네임 검증 실패 (공백 · 10자 초과 · '탈퇴' 예약 prefix 로 시작)",
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
            ApiResponse(
                responseCode = "404",
                description = "유저를 찾을 수 없음 (JWT 유효하지만 DB에서 유저가 삭제된 경우)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "상태 충돌 (닉네임 중복 · 탈퇴한 유저)",
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
        summary = "회원 탈퇴",
        description =
            "현재 로그인된 MEMBER 의 계정을 탈퇴 처리한다. users 행은 익명 tombstone 으로 남겨 공유 토너먼트 참조를 보존하고, " +
                "소셜 식별자(user_details)·기기 토큰(user_devices)은 즉시 하드삭제, 위시·알림은 soft-delete(30일 후 영구 파기) 한다. " +
                "refresh token 무효화·SSE 연결 종료까지 함께 처리한다. 게스트는 탈퇴 대상이 아니라 403 으로 거부한다. 멱등 — 재요청해도 200.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "탈퇴 성공 (data=null)",
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
            ApiResponse(
                responseCode = "403",
                description = "게스트는 탈퇴할 수 없음 (탈퇴는 MEMBER 전용)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "유저를 찾을 수 없음 (JWT 유효하지만 DB에서 유저가 삭제된 경우)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun withdraw(
        @Parameter(hidden = true) userId: UUID,
    ): ApiResponseBody<Unit>

    @Operation(
        summary = "닉네임 중복 체크",
        description =
            "닉네임이 이미 다른 유저에게 점유됐는지 확인한다. 회원 전환 / 닉네임 수정 전 사전 확인용. " +
                "본인의 현재 닉네임은 중복으로 잡지 않는다 — 자기 닉네임 유지 / 자기 닉네임으로 재확인 흐름이 자연스럽게 통과.",
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
    fun checkNickname(
        @Parameter(hidden = true) userId: UUID,
        request: NicknameCheckRequest,
    ): ApiResponseBody<NicknameCheckResponse>
}
