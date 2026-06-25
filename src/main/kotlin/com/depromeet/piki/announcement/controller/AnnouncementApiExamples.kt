package com.depromeet.piki.announcement.controller

import com.depromeet.piki.announcement.controller.dto.AnnouncementResponse
import com.depromeet.piki.announcement.domain.AnnouncementException
import com.depromeet.piki.common.openapi.OpenApiObjectMapper
import com.depromeet.piki.common.openapi.binds
import com.depromeet.piki.common.openapi.examples
import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.common.response.PageResponse
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

@Configuration
class AnnouncementApiExamples(
    private val openApiObjectMapper: OpenApiObjectMapper,
) {
    @Bean
    fun announcementOpenApiExamples(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            if (handlerMethod.binds(AnnouncementController::list)) {
                operation.examples(openApiObjectMapper.delegate) {
                    add(
                        status = HttpStatus.OK,
                        name = "조회 성공 (마지막 페이지)",
                        payload =
                            ApiResponseBody.ok(
                                data = listOf(sampleAnnouncement, olderAnnouncement),
                                pageResponse = PageResponse(nextCursor = null, hasNext = false),
                            ),
                    )
                    add(
                        status = HttpStatus.OK,
                        name = "조회 성공 (다음 페이지 있음)",
                        payload =
                            ApiResponseBody.ok(
                                data = listOf(sampleAnnouncement),
                                // nextCursor 는 (sentAt, id)를 base64 로 인코딩한 opaque 토큰 — 다음 요청의 cursor 로 그대로 넘긴다.
                                pageResponse = PageResponse(nextCursor = "MjAyNi0wNi0xOFQxMDowMDowMF80Mw", hasNext = true),
                            ),
                    )
                    add(
                        status = HttpStatus.OK,
                        name = "공지 없음 (빈 목록)",
                        payload =
                            ApiResponseBody.ok(
                                data = emptyList<AnnouncementResponse>(),
                                pageResponse = PageResponse(nextCursor = null, hasNext = false),
                            ),
                    )
                    add(AnnouncementException.invalidCursor(), name = "유효하지 않은 cursor")
                    unauthorized()
                }
            }
            if (handlerMethod.binds(AnnouncementController::get)) {
                operation.examples(openApiObjectMapper.delegate) {
                    add(status = HttpStatus.OK, name = "조회 성공", payload = ApiResponseBody.ok(data = sampleAnnouncement))
                    add(AnnouncementException.notFound(), name = "존재하지 않거나 미발송 공지")
                    unauthorized()
                }
            }
            operation
        }

    // body 는 마크다운 — example 도 마크다운으로 둬, FE 가 문서만 보고 "렌더해야 한다" 를 알 수 있게 한다.
    private val sampleAnnouncement =
        AnnouncementResponse(
            id = 43,
            title = "서비스 점검 안내",
            body = "## 점검 안내\n6월 20일 **02:00~04:00** 점검이 예정되어 있어요.\n\n- 점검 중 일부 기능 제한\n- [자세히 보기](https://piki.day)",
            sentAt = LocalDateTime.of(2026, 6, 18, 10, 0, 0),
        )

    private val olderAnnouncement =
        AnnouncementResponse(
            id = 42,
            title = "신규 기능 추가",
            body = "**토너먼트 결과 공유**가 추가되었어요. 친구와 결과를 나눠보세요!",
            sentAt = LocalDateTime.of(2026, 6, 15, 9, 30, 0),
        )
}
