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
            상품 페이지 URL 을 받아 위시리스트에 등록한다. 메타데이터(이름/가격/이미지) 추출은 외부 LLM 호출이라
            오래 걸리므로 동기로 기다리지 않는다. 등록 즉시 item.status=PROCESSING 인 항목을 201 로 반환하고,
            실제 파싱은 백그라운드에서 진행되어 READY(완료) 또는 FAILED(파싱 실패) 로 전이한다.
            클라이언트는 위시리스트 조회를 폴링해 status 변화를 확인한다. URL 형식 오류는 등록 전에 400 으로 거른다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "위시리스트 등록 접수 (item.status=PROCESSING, 파싱은 백그라운드)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (URL 이 비어 있음 · 유효한 URL 형식이 아님 · https 외 스킴)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "미인증 (JWT 토큰 없음 또는 유효하지 않음)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음 (GUEST 권한으로 접근 불가 · MEMBER 필요)",
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
            각 항목의 item.status 로 파싱 상태(PROCESSING/READY/FAILED)를 구분한다 —
            등록 직후 PROCESSING 인 항목은 이 조회를 폴링해 READY/FAILED 로 전이되는지 확인한다.
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
            ApiResponse(
                responseCode = "400",
                description = "유효하지 않은 cursor 값 (숫자로 변환 불가)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "미인증 (JWT 토큰 없음 또는 유효하지 않음)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음 (GUEST 권한으로 접근 불가 · MEMBER 필요)",
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
        summary = "위시리스트 단건 조회",
        description = """
            wishId 로 위시 항목 하나를 조회한다. 응답 모양은 목록 조회 항목과 같은 WishItemResponse(wish + item).
            본인 위시만 조회 가능하며, item 을 직접 노출하지 않고 위시 소유 단위로 권한을 검증한다.
            item.status 로 파싱 상태(PROCESSING/READY/FAILED)를 구분한다 — 상세 화면 진입 시 단건 폴링에 쓸 수 있다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "미인증 (JWT 토큰 없음 또는 유효하지 않음)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음 (GUEST 권한으로 접근 불가 · MEMBER 필요, 또는 본인 위시가 아님)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "존재하지 않는 위시 항목 (삭제된 항목 포함)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun getWish(
        @Parameter(hidden = true) userId: UUID,
        @Parameter(description = "위시 항목 ID", example = "1024") wishId: Long,
    ): ApiResponseBody<WishItemResponse>

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
                description = "잘못된 요청 (currentPrice 음수 · name/imageUrl/currency 길이 초과)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "미인증 (JWT 토큰 없음 또는 유효하지 않음)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음 (GUEST 권한으로 접근 불가 · MEMBER 필요, 또는 본인 위시가 아님)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "존재하지 않는 위시 항목 (삭제된 항목 포함)",
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
                responseCode = "401",
                description = "미인증 (JWT 토큰 없음 또는 유효하지 않음)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음 (GUEST 권한으로 접근 불가 · MEMBER 필요, 또는 본인 위시가 아님)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "존재하지 않는 위시 항목 (삭제된 항목 포함)",
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
        summary = "위시리스트 이미지 등록 (다건)",
        description = """
            상품 페이지를 캡처한 이미지 1~5장을 받아, 각 이미지를 PROCESSING 상태의 위시 항목으로 즉시 등록하고 목록을 반환한다.
            실제 상품 정보 추출(Gemini Vision)은 백그라운드에서 비동기로 진행되어 각 항목을 READY 또는 FAILED 로 전이시킨다.
            URL 등록과 결과 모양(WishItemResponse)이 같다. 이미지 등록 항목은 URL 이 없어 sourceUrl 이 null 이며,
            추출 결과는 조회로 폴링하거나 수정 API 로 교정한다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "이미지 등록 접수 — 각 항목이 PROCESSING 상태로 생성되고 비동기 파싱이 시작된다",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description =
                    "잘못된 요청 (이미지 개수 1~5 위반 · 빈 이미지 · 이미지 타입 미지정 · " +
                        "지원하지 않는 이미지 형식(png/jpeg/webp/heic/heif만 허용))",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "미인증 (JWT 토큰 없음 또는 유효하지 않음)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음 (GUEST 권한으로 접근 불가 · MEMBER 필요)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    fun registerFromImages(
        @Parameter(hidden = true) userId: UUID,
        images: List<MultipartFile>,
    ): ApiResponseBody<List<WishItemResponse>>
}
