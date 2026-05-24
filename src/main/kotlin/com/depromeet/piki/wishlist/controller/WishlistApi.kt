package com.depromeet.piki.wishlist.controller

import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.wishlist.controller.dto.WishItemResponse
import com.depromeet.piki.wishlist.controller.dto.WishlistRegisterRequest
import com.depromeet.piki.wishlist.controller.dto.WishlistUpdateRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Tag(name = "Wishlist", description = "위시리스트 등록/조회/수정/삭제 API")
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

    @Operation(
        summary = "위시리스트 수정",
        description = """
            위시 항목에 연결된 상품(item)의 이름·현재가·이미지·통화를 수정한다. 들어온 필드만 갱신한다.
            본인 위시만 수정 가능하며, item 을 직접 노출하지 않고 위시 소유 단위로 권한을 검증한다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "수정 성공",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (가격 음수, 이름 길이 초과 등)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "본인 위시가 아님",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "존재하지 않는 위시 항목",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun updateWish(
        @Parameter(hidden = true) userId: UUID,
        @Parameter(description = "위시 항목 ID", example = "1024") wishId: Long,
        request: WishlistUpdateRequest,
    ): ApiResponseBody<WishItemResponse>

    @Operation(
        summary = "위시리스트 삭제",
        description = """
            위시 항목을 삭제한다(soft delete). 본인 위시만 삭제 가능하다.
            삭제된 항목은 조회 결과에서 제외된다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "삭제 성공 (data 없음)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "본인 위시가 아님",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "존재하지 않는 위시 항목",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun deleteWish(
        @Parameter(hidden = true) userId: UUID,
        @Parameter(description = "위시 항목 ID", example = "1024") wishId: Long,
    ): ApiResponseBody<Unit>

    @Operation(
        summary = "위시리스트 OCR 등록",
        description = """
            상품 페이지를 캡처한 이미지를 받아 Gemini Vision 으로 상품명/가격을 추출한 뒤 유저의 위시리스트에 등록한다.
            URL 등록과 결과 모양(WishItemResponse)이 같다. OCR 항목은 URL 이 없어 sourceUrl 이 null 이며,
            추출이 부정확하면 수정 API 로 교정한다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "OCR 등록 성공",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (빈 이미지 / 지원하지 않는 이미지 형식 등)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "502",
                description = "Gemini 호출/응답 처리 실패",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun registerFromOcr(
        @Parameter(hidden = true) userId: UUID,
        image: MultipartFile,
    ): ApiResponseBody<WishItemResponse>
}
