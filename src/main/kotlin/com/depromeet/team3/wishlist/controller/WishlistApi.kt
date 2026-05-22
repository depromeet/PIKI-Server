package com.depromeet.team3.wishlist.controller

import com.depromeet.team3.common.response.ApiResponseBody
import com.depromeet.team3.wishlist.controller.dto.WishItemResponse
import com.depromeet.team3.wishlist.controller.dto.WishlistRegisterRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import java.util.UUID

@Tag(name = "Wishlist", description = "위시리스트 등록/조회 API")
interface WishlistApi {
    @Operation(
        summary = "위시리스트 등록",
        description = """
            상품 페이지 URL 을 받아 메타데이터(이름/가격/이미지 등)를 추출한 뒤 유저의 위시리스트에 등록한다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "위시리스트 등록 성공",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (URL 형식 오류 등)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun register(
        @Parameter(hidden = true) userId: UUID,
        request: WishlistRegisterRequest,
    ): ApiResponseBody<WishItemResponse>

    @Operation(
        summary = "위시리스트 조회",
        description = """
            로그인한 유저 본인의 위시리스트를 최신 등록순(id desc)으로 조회한다.
            cursor 페이지네이션: 직전 응답의 pageResponse.nextCursor 를 다음 요청 cursor 로 그대로 전달한다.
            마지막 페이지면 nextCursor 는 null, hasNext 는 false.
            size 는 미지정 시 20, 1~50 범위를 벗어나면 양 끝으로 보정된다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "위시리스트 조회 성공",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun getWishlist(
        @Parameter(hidden = true) userId: UUID,
        @Parameter(description = "직전 응답의 nextCursor (없으면 첫 페이지)", example = "1010")
        cursor: String?,
        @Parameter(description = "페이지 크기 (기본 20, 최대 50)", example = "20")
        size: Int?,
    ): ApiResponseBody<List<WishItemResponse>>
}
