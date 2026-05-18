package com.depromeet.team3.auth.controller

import com.depromeet.team3.auth.controller.dto.GuestCreateResponse
import com.depromeet.team3.common.response.ApiResponseBody
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Dev", description = "개발 전용 API - Task 6 OAuth 완료 후 제거 예정")
interface DevAuthApi {
    @Operation(
        summary = "더미 MEMBER 생성",
        description = "OAuth 없이 MEMBER 유저를 생성하고 토큰을 발급한다. local/dev 환경 전용.",
    )
    fun createDummyMember(nickname: String?): ApiResponseBody<GuestCreateResponse>
}
