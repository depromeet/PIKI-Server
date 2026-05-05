package com.depromeet.team3.guest.controller

import com.depromeet.team3.common.response.ApiResponseBody
import com.depromeet.team3.guest.controller.dto.GuestResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType

@Tag(name = "Guest", description = "게스트 식별자 발급 API")
interface GuestApi {
    @Operation(
        summary = "게스트 ID 발급",
        description = """
            로그인 없이 위시리스트를 사용하는 게스트에게 영속 식별자(UUID v4)를 발급한다.
            클라이언트는 발급받은 값을 안전하게 보관하고, 이후 위시리스트 API 호출 시 함께 전달한다.
        """,
    )
    @ApiResponse(
        responseCode = "200",
        description = "게스트 ID 발급 성공",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ApiResponseBody::class),
            ),
        ],
    )
    fun issueGuestId(): ApiResponseBody<GuestResponse>
}
