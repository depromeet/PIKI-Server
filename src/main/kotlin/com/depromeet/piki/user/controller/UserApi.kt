package com.depromeet.piki.user.controller

import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.user.controller.dto.MyProfileResponse
import com.depromeet.piki.user.controller.dto.NicknameCheckRequest
import com.depromeet.piki.user.controller.dto.NicknameCheckResponse
import com.depromeet.piki.user.controller.dto.UserUpdateRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Tag(name = "User", description = "유저 API")
interface UserApi {
    @Operation(
        summary = "내 정보 조회",
        description =
            "현재 로그인된 유저(GUEST 포함) 의 정보를 조회한다. 소셜 계정 email 을 함께 내려준다 " +
                "(미수집·미동의·backfill 전이면 null).",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공 (email 은 미수집·미동의 시 null)",
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
    ): ApiResponseBody<MyProfileResponse>

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
                description = "수정 성공 (조회와 동일한 내 정보 모양, email 은 미수집·미동의 시 null)",
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
    ): ApiResponseBody<MyProfileResponse>

    @Operation(
        summary = "회원 탈퇴",
        description =
            "현재 로그인된 MEMBER 의 계정을 탈퇴 처리한다. users 행은 익명 tombstone 으로 남겨 공유 토너먼트 참조를 보존하고, " +
                "소셜 식별자(user_details)·기기 토큰(user_devices)·위시·알림은 즉시 하드삭제 한다(PIPA 지체없이 파기). " +
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
        summary = "프로필 이미지 수정",
        description =
            "프로필 이미지를 교체한다. multipart/form-data 의 image 파트로 사진 파일을 올리면 서버가 S3 에 저장한 뒤 " +
                "그 URL 로 내 profileImage 를 갱신한다. GUEST 도 호출할 수 있다. " +
                "허용 형식: png/jpeg/webp/heic/heif (gif·svg 등 그 외 형식은 400). 파일 크기는 5MB 이하. " +
                "HEIC(iOS 기본 카메라 포맷)도 변환 없이 그대로 저장하므로, 웹 표시 호환은 클라이언트 업로드 정책에 맡긴다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "수정 성공 (갱신된 profileImage URL 포함, 조회와 동일한 내 정보 모양·email 포함)",
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
                    "잘못된 요청 (image 파트 미첨부 · 빈 이미지 파일 · 이미지 타입 미지정 · 지원하지 않는 형식(png/jpeg/webp/heic/heif만 허용) · " +
                        "선언한 Content-Type 과 실제 파일 내용 불일치(헤더 위조·파일 손상))",
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
                description = "상태 충돌 (탈퇴한 유저)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "413",
                description = "파일 크기가 허용 한도(multipart max-file-size)를 초과함",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "502",
                description = "외부 의존성 실패 (이미지 저장소(S3) 업로드 실패 — 재시도 가능)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun updateProfileImage(
        @Parameter(hidden = true) userId: UUID,
        image: MultipartFile?,
    ): ApiResponseBody<MyProfileResponse>

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
        @Parameter(hidden = true) userId: UUID?,
        request: NicknameCheckRequest,
    ): ApiResponseBody<NicknameCheckResponse>
}
