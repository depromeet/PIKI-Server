package com.depromeet.piki.notification.fcm.controller

import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.notification.fcm.controller.dto.DevPushRequest
import com.depromeet.piki.notification.fcm.controller.dto.DevPushResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import java.util.UUID

@Tag(name = "Dev", description = "개발·테스트 전용 API (운영 환경 비활성화)")
interface DevFcmApi {
    @Operation(
        summary = "[DEV] FCM 즉시 발송 (토큰 직접 지정)",
        description =
            "본문의 FCM 토큰으로 즉시 푸시를 발송한다. FE 가 Xcode 에서 받은 토큰을 Postman 으로 던져 " +
                "\"우리 서버 → FCM → 내 기기\" 도달을 자가 확인하는 개발 도구다. 등록(#244) 없이 토큰만으로 쏜다.\n\n" +
                "- 발송은 운영과 동일한 `FirebaseMessageSender` 를 태운다. `@Profile(\"!prod\")` 라 운영에는 라우트가 없다.\n" +
                "- **인증** — `/api/v1/dev/**` 는 GUEST 권한이 필요하다 (`POST /api/v1/auth/guest` 로 게스트 토큰 발급 후 Bearer).\n\n" +
                "**응답 해석**\n\n" +
                "| 필드 | 의미 |\n" +
                "|---|---|\n" +
                "| `fcmEnabled=false` | 이 서버에 `FIREBASE_SERVICE_ACCOUNT` 가 없어 발송이 no-op |\n" +
                "| `staleTokenCount=1` | 그 토큰이 무효/만료 (앱 삭제 등) |",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "발송 위임 성공 (fcmEnabled·staleTokenCount 로 실제 발송 여부 확인)",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (token 비어 있음 · 길이 초과 · badge 가 음수)",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "미인증 (JWT 토큰 없음 또는 유효하지 않음)",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "GUEST 권한 없음 (/api/v1/dev/** 는 GUEST 권한 필요)",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
        ],
    )
    fun push(
        @Parameter(hidden = true) userId: UUID,
        request: DevPushRequest,
    ): ApiResponseBody<DevPushResponse>
}
