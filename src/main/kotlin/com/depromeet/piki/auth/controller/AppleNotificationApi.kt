package com.depromeet.piki.auth.controller

import com.depromeet.piki.common.response.ApiResponseBody
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType

@Tag(name = "Auth", description = "인증 API")
interface AppleNotificationApi {
    @Operation(
        summary = "Apple 서버-서버 알림 수신",
        description =
            "**Apple 이 직접 호출하는 서버-서버 엔드포인트다 (클라이언트 앱이 호출하지 않는다).**\n\n" +
                "사용자가 Apple 쪽에서 \"Apple 로 로그인\" 연결을 끊거나 Apple ID 를 삭제하면, Apple 이 이 경로로 " +
                "서명된 알림(JWT)을 보낸다. 우리는 서명을 검증한 뒤 계정 상태를 동기화한다. " +
                "엔드포인트 URL 은 Apple Developer Console 의 Server-to-Server Notification Endpoint 에 등록해야 동작한다.\n\n" +
                "요청은 `application/x-www-form-urlencoded` 의 `payload` 필드에 서명된 JWT 로 담겨 온다. " +
                "진위는 오직 그 JWT 서명(Apple JWKS)·issuer·aud 로 가린다 (우리 JWT 인증 없음).\n\n" +
                "**이벤트별 처리**\n\n" +
                "| 이벤트 | 의미 | 처리 |\n" +
                "|---|---|---|\n" +
                "| `account-delete` | Apple ID 자체 삭제 | 회원 탈퇴 (데이터 파기) |\n" +
                "| `consent-revoked` | 앱-Apple 연결 해제 | 세션 종료(로그아웃). 계정·데이터는 유지, 재로그인 시 복귀 |\n" +
                "| `email-disabled` / `email-enabled` | Private Relay 전달 on/off | 로그만 (메일 발송 안 함) |\n\n" +
                "- 대상 유저가 없거나(미가입·이미 탈퇴) 미지원 이벤트면 멱등하게 200 으로 흡수한다 " +
                "(Apple 은 2xx 가 아니면 재시도하므로).",
    )
    // Apple 이 인증 없이 호출하는 진입점 (SecurityConfig 의 permitAll). 글로벌 Bearer 요구를 해제한다.
    @SecurityRequirements
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "알림 처리 완료 (멱등 — 대상 유저 없음·이미 탈퇴·미지원 이벤트 포함)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "유효하지 않은 Apple 알림 (서명·issuer·aud 검증 실패 또는 payload 형식 오류 = 위조/비정상 호출)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun handle(
        @Parameter(description = "Apple 이 보내는 서명된 알림 JWT (form 필드 payload)")
        payload: String,
    ): ApiResponseBody<Unit>
}
