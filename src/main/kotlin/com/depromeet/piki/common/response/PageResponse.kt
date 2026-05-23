package com.depromeet.piki.common.response

import io.swagger.v3.oas.annotations.media.Schema

data class PageResponse(
    @field:Schema(description = "다음 페이지 조회용 커서. 다음 페이지가 없으면 null", example = "1024", nullable = true)
    val nextCursor: String?,
    @field:Schema(description = "다음 페이지 존재 여부", example = "false")
    val hasNext: Boolean,
)
