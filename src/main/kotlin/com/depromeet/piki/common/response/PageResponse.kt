package com.depromeet.piki.common.response

import io.swagger.v3.oas.annotations.media.Schema

data class PageResponse(
    @field:Schema(description = "다음 페이지 조회용 커서. 다음 페이지가 없으면 null", example = "1024", nullable = true)
    val nextCursor: String?,
    @field:Schema(description = "다음 페이지 존재 여부", example = "false")
    val hasNext: Boolean,
) {
    companion object {
        // 페이징과 무관한 응답에도 동일한 응답 구조를 유지하기 위한 "다음 페이지 없음" 기본값.
        val EMPTY = PageResponse(nextCursor = null, hasNext = false)
    }
}
