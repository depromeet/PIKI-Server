package com.depromeet.piki.wishlist.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

@Schema(description = "위시리스트 다중 삭제 요청")
data class WishlistBulkDeleteRequest(
    @field:NotEmpty(message = "삭제할 위시 ID 목록은 비어 있을 수 없습니다.")
    @field:Size(max = MAX_DELETE_COUNT, message = "한 번에 최대 ${MAX_DELETE_COUNT}개까지 삭제할 수 있습니다.")
    @field:Schema(
        description = "삭제할 위시 항목 ID 목록 (중복은 무시된다)",
        example = "[1024, 1025, 1026]",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val wishIds: List<Long>,
) {
    companion object {
        // 한 번에 삭제 가능한 상한. 조회 페이지 상한(50)을 넉넉히 넘되 IN 절이 과대해지지 않는 선.
        const val MAX_DELETE_COUNT = 100
    }
}
