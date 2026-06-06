package com.depromeet.piki.notification.controller

import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.notification.controller.dto.NotificationSsePayload
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

@Tag(name = "Notification", description = "알림 API")
interface NotificationSseApi {
    @Operation(
        summary = "알림 실시간 구독 (SSE)",
        description = """
            인증 유저의 알림을 실시간으로 받는 SSE(Server-Sent Events) 스트림을 연다.
            응답은 ApiResponseBody JSON 래퍼가 아니라 text/event-stream 스트림이며, 다음 이벤트가 흘러온다.
            - connect: 구독 직후 1회. data="connected". 연결 성립 신호.
            - notification: 알림 1건. data 는 NotificationSsePayload JSON (200 응답 스키마 참고). type+refId 로 딥링크를 분기한다.
            - (주석 ping): 약 30초 간격 하트비트. 연결 유지용이며 data 이벤트가 아니다.
            토너먼트 알림은 해당 토너먼트 참여자에게만 fan-out 되므로, 자기 스트림 1개만 구독하면 토너먼트·개인 알림이 모두 도착한다.
            연결은 30분 후 타임아웃되며, 클라이언트는 끊기면 재연결한다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "SSE 스트림 시작 (text/event-stream). 이하 schema 는 notification 이벤트의 data payload 형태다.",
                content = [
                    Content(
                        mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                        schema = Schema(implementation = NotificationSsePayload::class),
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
    fun subscribe(
        @Parameter(hidden = true) userId: UUID,
    ): SseEmitter
}
