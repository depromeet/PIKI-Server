package com.depromeet.piki.notification.controller

import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.notification.controller.dto.NotificationHistoryResponse
import com.depromeet.piki.notification.controller.dto.NotificationReadRequest
import com.depromeet.piki.notification.controller.dto.NotificationReadResponse
import com.depromeet.piki.notification.domain.NotificationCategory
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
                "**카테고리 필터**\n\n" +
                "- `category` 미지정 시 전체. `ACTIVITY`(활동: 토너먼트 소셜 알림) / `SYSTEM`(시스템: 파싱·공지)로 탭 필터.\n" +
                "- `unreadCount` 는 category 와 무관하게 항상 전체 안읽음 수(앱 badge)다.\n" +
                "- `unreadCountByCategory` 로 탭별 안읽음 수(탭 badge)를 함께 내려준다 — 모든 카테고리 키 포함, 없으면 0.\n\n" +
                "**응답 활용**\n\n" +
                "- 응답 `data` 의 `unreadCount`(앱 badge) · `unreadCountByCategory`(탭 badge)로 안읽음 수를 함께 내려준다 (별도 카운트 API 없음).\n" +
                "- 각 항목의 `imageUrl` 은 항상 채워진다 — 사람 알림은 발송 시점 프사, 시스템 알림은 피키 로고. " +
                "사람/시스템 구분은 `category`. 클라는 `imageUrl` 을 그대로 아바타로 렌더한다.\n" +
                "- 각 항목 셰입은 SSE `notification` 이벤트 payload 와 동일하다 — `type` 으로 화면을, " +
                "`refId` 로 딥링크 이동을, 파싱 알림은 `kind` 로 출처(위시/토너먼트)를 분기하고, `id` 로 단건 읽음 처리(`POST /read`)를 한다.\n\n" +
                "**알림 타입 카탈로그 (전 9종)**\n\n" +
                "`type` 으로 화면을 분기하고 `refId` 로 이동 대상을 정한다. `body` 는 현재 전 타입 빈 문자열(`\"\"`).\n\n" +
                "| `type` | 트리거 | 카테고리 | `refId` | `title` 예시 | 아바타(`imageUrl`) |\n" +
                "|---|---|---|---|---|---|\n" +
                "| `TOURNAMENT_JOINED` | 토너먼트 참가 | ACTIVITY | tournamentId | {참가자}님이 참가했어요 | 행위자 프사 |\n" +
                "| `TOURNAMENT_ITEM_ADDED` | 아이템 추가 | ACTIVITY | tournamentId | {참가자}님이 아이템을 추가했어요 | 행위자 프사 |\n" +
                "| `TOURNAMENT_STARTED` | 토너먼트 시작 | ACTIVITY | tournamentId | {주최자}님이 토너먼트를 시작했어요 | 행위자 프사 |\n" +
                "| `TOURNAMENT_PLAYED_FROM_LINK` | 플레이링크로 플레이 시작 | ACTIVITY | ROOT 토너먼트 id | {플레이어}님이 회원님 토너먼트를 플레이했어요 | 행위자 프사 |\n" +
                "| `TOURNAMENT_COMPLETED` | 멤버가 클론 완료 | ACTIVITY | ROOT 토너먼트 id | {멤버}님이 회원님 토너먼트를 완료했어요 | 행위자 프사 |\n" +
                "| `TOURNAMENT_RESULT_READY` | 주최자가 ROOT 완료 | ACTIVITY | ROOT 토너먼트 id | 참여하신 {주최자}님의 토너먼트 결과가 나왔어요 | 주최자 프사 |\n" +
                "| `ITEM_PARSING_COMPLETED` | 상품 추출 성공 | SYSTEM | itemId | 상품 정보가 저장됐어요 | 피키 로고 |\n" +
                "| `ITEM_PARSING_FAILED` | 상품 추출 실패 | SYSTEM | itemId | 상품 정보를 가져오지 못했어요 | 피키 로고 |\n" +
                "| `ANNOUNCEMENT` | 관리자 공지(후속) | SYSTEM | 공지 id/0 | (관리자 입력) | 피키 로고 |\n\n" +
                "> 파싱 알림(`ITEM_PARSING_*`)만 출처별 `kind`(WISH/TOURNAMENT)·`tournamentId`·`tournamentItemId` 가 추가로 실린다. " +
                "나머지 타입엔 그 키가 없다. `title` 은 발송 시점 렌더 값이라 클라는 문구가 아니라 `type` 으로 분기한다.",
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
                description = "유효하지 않은 cursor 값 (숫자로 변환 불가) · 유효하지 않은 category 값 (ACTIVITY/SYSTEM 외)",
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
        @Parameter(description = "카테고리 필터 (미지정 시 전체). ACTIVITY(활동) / SYSTEM(시스템)", example = "ACTIVITY")
        category: NotificationCategory?,
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
                "- 응답 `data` 의 `unreadCount`(앱 badge) · `unreadCountByCategory`(탭 badge)로 처리 후 안읽음 수를 **서버 권위 값**으로 내려준다 — " +
                "클라는 이 값들을 그대로 badge 로 미러링한다(별도 카운트 조회 불필요).",
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
