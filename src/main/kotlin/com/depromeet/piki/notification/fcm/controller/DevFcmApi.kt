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

@Tag(name = "FCM", description = "FCM 푸시 토큰 API")
interface DevFcmApi {
    @Operation(
        summary = "[DEV] FCM 즉시 발송",
        description = """
            인증 유저 본인의 모든 등록 기기로 푸시를 즉시 발송한다. 수신자 정책(#236) 없이 발송 경로
            (PushNotificationChannel → FirebaseMessageSender)가 실제 FCM 에 닿는지 확인하는 개발 전용 도구다.
            반복 호출해 무한 발송 테스트가 가능하다. @Profile("!prod") 라 운영에는 라우트 자체가 없다.
            FIREBASE_SERVICE_ACCOUNT 미설정 환경에선 fcmEnabled=false 로 실제 발송은 no-op 이다(응답은 200).
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "발송 위임 성공 (targetTokenCount·fcmEnabled 로 실제 발송 여부를 확인한다)",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ApiResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (title 또는 body 가 255자를 초과)",
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
