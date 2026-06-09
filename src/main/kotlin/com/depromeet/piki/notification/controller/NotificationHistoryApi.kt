package com.depromeet.piki.notification.controller

import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.notification.controller.dto.NotificationHistoryResponse
import com.depromeet.piki.notification.controller.dto.NotificationReadRequest
import com.depromeet.piki.notification.controller.dto.NotificationReadResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import java.util.UUID

@Tag(name = "Notification", description = "알림 API")
interface NotificationHistoryApi {
    @Operation(
        summary = "알림 히스토리 조회",
        description =
            "로그인한 유저 본인의 알림을 **최신순(`id` desc)** 으로 조회한다 (**GUEST·MEMBER 모두**).\n\n" +
                "**커서 페이지네이션**\n\n" +
                "- 직전 응답의 `pageResponse.nextCursor` 를 다음 요청 `cursor` 로 그대로 전달한다.\n" +
                "- 마지막 페이지면 `nextCursor` 는 `null`, `hasNext` 는 `false`.\n" +
                "- `size` 는 미지정 시 20, 1~50 범위를 벗어나면 양 끝으로 보정된다.\n\n" +
                "**응답 활용**\n\n" +
                "- 응답 `data` 의 `unreadCount` 로 안읽음 수(badge)를 함께 내려준다 (별도 카운트 API 없음).\n" +
                "- 각 항목 셰입은 SSE `notification` 이벤트 payload 와 동일하다 — `type` 으로 화면을, 파싱 알림은 " +
                "`kind` 로 출처(위시/토너먼트)를 분기하고, `id` 로 단건 읽음 처리(`POST /read`)·딥링크 이동을 한다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공 (목록 + unreadCount)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "유효하지 않은 cursor 값 (숫자로 변환 불가)",
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
    fun getHistory(
        @Parameter(hidden = true) userId: UUID,
        @Parameter(description = "직전 응답의 nextCursor (없으면 첫 페이지)", example = "1010")
        cursor: String?,
        @Parameter(description = "페이지 크기 (기본 20, 최대 50)", example = "20")
        size: Int?,
    ): ApiResponseBody<NotificationHistoryResponse>

    @Operation(
        summary = "알림 읽음 처리",
        description =
            "알림을 읽음 처리한다 (**GUEST·MEMBER 모두, 본인 알림만**). 요청 body 는 두 방식 중 **정확히 하나**:\n\n" +
                "| 방식 | 동작 |\n" +
                "|---|---|\n" +
                "| `all=true` | 본인 안읽음 알림 전부 읽음 (전체 읽음 버튼, 화면 이동 없음) |\n" +
                "| `ids=[...]` | 지정한 알림만 읽음 (단건 클릭은 `[id]` 1개, 클릭 후 FE 가 딥링크로 이동) |\n\n" +
                "- 둘 다 보내거나 둘 다 비우면(빈 `ids` 포함) **400**.\n" +
                "- `ids` 는 본인 소유만 반영되고 타인·없는 id 는 무시된다. **멱등**(이미 읽음도 성공).\n" +
                "- 응답 `data` 의 `unreadCount` 로 처리 후 안읽음 수(badge)를 **서버 권위 값**으로 내려준다 — " +
                "클라는 이 값을 그대로 badge 로 미러링한다(별도 카운트 조회 불필요).",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "읽음 처리 성공 (처리 후 unreadCount 동봉, 멱등)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (all 과 ids 를 함께 보냄 · 둘 다 없음 · 빈 ids)",
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
    fun read(
        @Parameter(hidden = true) userId: UUID,
        request: NotificationReadRequest,
    ): ApiResponseBody<NotificationReadResponse>
}
