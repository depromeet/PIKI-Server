package com.depromeet.piki.notification.controller

import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.openapi.OpenApiObjectMapper
import com.depromeet.piki.common.openapi.binds
import com.depromeet.piki.common.openapi.examples
import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.common.response.PageResponse
import com.depromeet.piki.notification.controller.dto.NotificationHistoryResponse
import com.depromeet.piki.notification.controller.dto.NotificationReadRequest
import com.depromeet.piki.notification.controller.dto.NotificationSsePayload
import com.depromeet.piki.notification.domain.NotificationException
import com.depromeet.piki.notification.domain.NotificationType
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

@Configuration
class NotificationHistoryApiExamples(
    private val openApiObjectMapper: OpenApiObjectMapper,
) {
    @Bean
    fun notificationHistoryOpenApiExamples(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            if (handlerMethod.binds(NotificationHistoryController::getHistory)) {
                operation.examples(openApiObjectMapper.delegate) {
                    add(
                        status = HttpStatus.OK,
                        name = "조회 성공 (안읽음·읽음 혼재, 마지막 페이지)",
                        payload =
                            ApiResponseBody.ok(
                                data = NotificationHistoryResponse(items = sampleItems, unreadCount = 2),
                                pageResponse = PageResponse(nextCursor = null, hasNext = false),
                            ),
                    )
                    add(
                        status = HttpStatus.OK,
                        name = "조회 성공 (다음 페이지 있음)",
                        payload =
                            ApiResponseBody.ok(
                                data = NotificationHistoryResponse(items = listOf(tournamentParsingItem), unreadCount = 1),
                                pageResponse = PageResponse(nextCursor = "1024", hasNext = true),
                            ),
                    )
                    add(
                        status = HttpStatus.OK,
                        name = "빈 알림함",
                        payload =
                            ApiResponseBody.ok(
                                data = NotificationHistoryResponse(items = emptyList(), unreadCount = 0),
                                pageResponse = PageResponse(nextCursor = null, hasNext = false),
                            ),
                    )
                    add(NotificationException.invalidCursor(), name = "유효하지 않은 cursor")
                    unauthorized()
                }
            }
            if (handlerMethod.binds(NotificationHistoryController::read)) {
                operation.examples(openApiObjectMapper.delegate) {
                    add(
                        status = HttpStatus.OK,
                        name = "읽음 처리 성공",
                        payload = ApiResponseBody.ok<Unit>(),
                    )
                    add(
                        status = HttpStatus.BAD_REQUEST,
                        name = "all 과 ids 동시 전송 / 둘 다 없음 / 빈 ids",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.INVALID_INPUT,
                                // @AssertTrue(validSelection) 위반은 GlobalExceptionHandler.detailOf 가 "필드명: 메시지" 로 만든다.
                                detail = "validSelection: ${NotificationReadRequest.VALID_SELECTION_MESSAGE}",
                            ),
                    )
                    unauthorized()
                }
            }
            operation
        }

    // 토너먼트 알림 (라우팅 없음, refId 만) — 안읽음.
    private val referenceItem =
        NotificationSsePayload.Reference(
            id = 1026,
            type = NotificationType.TOURNAMENT_JOINED,
            title = "토너먼트에 참가했어요",
            body = "지금 바로 픽을 시작해보세요",
            refId = 77,
            isRead = false,
            createdAt = LocalDateTime.of(2026, 6, 8, 10, 10, 0),
        )

    // 위시 출처 파싱 완료 알림 (kind=WISH) — 읽음.
    private val wishParsingItem =
        NotificationSsePayload.WishParsing(
            id = 1025,
            type = NotificationType.ITEM_PARSING_COMPLETED,
            title = "상품 정보가 준비됐어요",
            body = "에어 조던 1 미드",
            refId = 512,
            isRead = true,
            createdAt = LocalDateTime.of(2026, 6, 8, 10, 5, 0),
        )

    // 토너먼트 출처 파싱 완료 알림 (kind=TOURNAMENT + 두 식별자) — 안읽음.
    private val tournamentParsingItem =
        NotificationSsePayload.TournamentParsing(
            id = 1024,
            type = NotificationType.ITEM_PARSING_COMPLETED,
            title = "토너먼트 아이템이 준비됐어요",
            body = "나이키 덩크 로우",
            refId = 513,
            isRead = false,
            createdAt = LocalDateTime.of(2026, 6, 8, 10, 0, 0),
            tournamentId = 99,
            tournamentItemId = 555,
        )

    private val sampleItems = listOf(referenceItem, wishParsingItem, tournamentParsingItem)
}
