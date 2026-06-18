package com.depromeet.piki.announcement.controller

import com.depromeet.piki.announcement.controller.dto.AnnouncementResponse
import com.depromeet.piki.announcement.domain.AnnouncementException
import com.depromeet.piki.announcement.service.AnnouncementCursor
import com.depromeet.piki.announcement.service.AnnouncementQueryService
import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.common.response.PageResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/announcements")
class AnnouncementController(
    private val announcementQueryService: AnnouncementQueryService,
) : AnnouncementApi {
    @GetMapping
    override fun list(
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "${AnnouncementQueryService.DEFAULT_PAGE_SIZE}") size: Int,
    ): ApiResponseBody<List<AnnouncementResponse>> {
        // cursor 가 있으나 디코딩 안 되면(변조·잘못된 형식) 입력 경계 계약 위반 → 400. 없으면 첫 페이지.
        val cursorKey = cursor?.let { AnnouncementCursor.decode(it) ?: throw AnnouncementException.invalidCursor() }
        val page = announcementQueryService.listSent(cursorKey, size)
        return ApiResponseBody.ok(
            data = page.items.map(AnnouncementResponse::from),
            pageResponse = PageResponse(nextCursor = page.nextCursor?.encode(), hasNext = page.hasNext),
        )
    }

    @GetMapping("/{announcementId}")
    override fun get(
        @PathVariable announcementId: Long,
    ): ApiResponseBody<AnnouncementResponse> = ApiResponseBody.ok(AnnouncementResponse.from(announcementQueryService.getSent(announcementId)))
}
