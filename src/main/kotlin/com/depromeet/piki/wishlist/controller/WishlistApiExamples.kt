package com.depromeet.piki.wishlist.controller

import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.openapi.OpenApiObjectMapper
import com.depromeet.piki.common.openapi.binds
import com.depromeet.piki.common.openapi.examples
import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.common.response.PageResponse
import com.depromeet.piki.image.domain.ProductImage
import com.depromeet.piki.item.domain.ItemStatus
import com.depromeet.piki.wishlist.controller.dto.WishItemResponse
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

@Configuration
class WishlistApiExamples(
    private val openApiObjectMapper: OpenApiObjectMapper,
) {
    @Bean
    fun wishlistOpenApiExamples(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            if (handlerMethod.binds(WishlistController::registerFromUrl)) {
                operation.examples(openApiObjectMapper.delegate) {
                    add(
                        status = HttpStatus.CREATED,
                        name = "등록 접수 (파싱 중)",
                        payload = ApiResponseBody.created(processingSampleEntry),
                    )
                    add(
                        status = HttpStatus.BAD_REQUEST,
                        name = "URL 형식 오류",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.INVALID_INPUT,
                                detail = "지원하지 않는 URL 형식입니다.",
                            ),
                    )
                    unauthorized()
                    forbidden("권한 없음 (MEMBER 필요)")
                }
            }
            if (handlerMethod.binds(WishlistController::getWishlist)) {
                operation.examples(openApiObjectMapper.delegate) {
                    add(
                        status = HttpStatus.OK,
                        name = "조회 성공 (담는 중 + 완성 혼재, 마지막 페이지)",
                        payload =
                            ApiResponseBody.ok(
                                data = listOf(processingSampleEntry, sampleEntry),
                                pageResponse = PageResponse(nextCursor = null, hasNext = false),
                            ),
                    )
                    add(
                        status = HttpStatus.OK,
                        name = "조회 성공 (다음 페이지 있음)",
                        payload =
                            ApiResponseBody.ok(
                                data = listOf(sampleEntry),
                                pageResponse = PageResponse(nextCursor = "1024", hasNext = true),
                            ),
                    )
                    add(
                        status = HttpStatus.OK,
                        name = "빈 위시리스트",
                        payload =
                            ApiResponseBody.ok(
                                data = emptyList<WishItemResponse>(),
                                pageResponse = PageResponse(nextCursor = null, hasNext = false),
                            ),
                    )
                    add(
                        status = HttpStatus.BAD_REQUEST,
                        name = "유효하지 않은 cursor",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.INVALID_INPUT,
                                detail = "유효하지 않은 cursor 입니다.",
                            ),
                    )
                    unauthorized()
                    forbidden("권한 없음 (MEMBER 필요)")
                }
            }
            if (handlerMethod.binds(WishlistController::getWish)) {
                operation.examples(openApiObjectMapper.delegate) {
                    add(
                        status = HttpStatus.OK,
                        name = "조회 성공",
                        payload = ApiResponseBody.ok(sampleEntry),
                    )
                    add(
                        status = HttpStatus.FORBIDDEN,
                        name = "본인 위시 아님",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.FORBIDDEN,
                                detail = "해당 위시 아이템에 접근할 권한이 없습니다.",
                            ),
                    )
                    add(
                        status = HttpStatus.NOT_FOUND,
                        name = "존재하지 않는 위시 항목",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.NOT_FOUND,
                                detail = "존재하지 않는 위시리스트 항목입니다.",
                            ),
                    )
                    unauthorized()
                }
            }
            if (handlerMethod.binds(WishlistController::recoverWishItem)) {
                operation.examples(openApiObjectMapper.delegate) {
                    add(
                        status = HttpStatus.OK,
                        name = "FAILED 보정 성공 (READY 로 복구)",
                        payload = ApiResponseBody.ok(sampleEntry),
                    )
                    add(
                        status = HttpStatus.BAD_REQUEST,
                        name = "가격 음수",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.INVALID_INPUT,
                                detail = "가격은 0 이상이어야 합니다.",
                            ),
                    )
                    add(
                        status = HttpStatus.BAD_REQUEST,
                        name = "상품명 없이 복구 시도",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.INVALID_INPUT,
                                detail = "상품명을 입력해야 합니다.",
                            ),
                    )
                    add(
                        status = HttpStatus.FORBIDDEN,
                        name = "본인 위시 아님",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.FORBIDDEN,
                                detail = "해당 위시 아이템에 접근할 권한이 없습니다.",
                            ),
                    )
                    add(
                        status = HttpStatus.NOT_FOUND,
                        name = "존재하지 않는 위시 항목",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.NOT_FOUND,
                                detail = "존재하지 않는 위시리스트 항목입니다.",
                            ),
                    )
                    add(
                        status = HttpStatus.CONFLICT,
                        name = "이미 등록 완료(READY) 항목 — 수정 불가",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.CONFLICT,
                                detail = "이미 등록 완료된 상품은 수정할 수 없습니다.",
                            ),
                    )
                    add(
                        status = HttpStatus.CONFLICT,
                        name = "아직 처리 중(PROCESSING) 항목 — 수정 불가",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.CONFLICT,
                                detail = "아직 처리 중인 상품은 수정할 수 없습니다.",
                            ),
                    )
                    add(
                        status = HttpStatus.BAD_GATEWAY,
                        name = "이미지 저장 실패",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.RETRYABLE,
                                detail = "이미지 저장에 실패했습니다. 잠시 후 다시 시도해 주세요.",
                            ),
                    )
                    unauthorized()
                }
            }
            if (handlerMethod.binds(WishlistController::deleteWish)) {
                operation.examples(openApiObjectMapper.delegate) {
                    add(
                        status = HttpStatus.OK,
                        name = "삭제 성공",
                        payload = ApiResponseBody.ok<Unit>(),
                    )
                    add(
                        status = HttpStatus.FORBIDDEN,
                        name = "본인 위시 아님",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.FORBIDDEN,
                                detail = "해당 위시 아이템에 접근할 권한이 없습니다.",
                            ),
                    )
                    unauthorized()
                }
            }
            if (handlerMethod.binds(WishlistController::deleteWishes)) {
                operation.examples(openApiObjectMapper.delegate) {
                    add(
                        status = HttpStatus.OK,
                        name = "다중 삭제 성공",
                        payload = ApiResponseBody.ok<Unit>(),
                    )
                    add(
                        status = HttpStatus.BAD_REQUEST,
                        name = "ids 누락/빈 목록/100개 초과",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.INVALID_INPUT,
                                detail = "삭제할 위시 ID 는 1개 이상 100개 이하여야 합니다.",
                            ),
                    )
                    add(
                        status = HttpStatus.FORBIDDEN,
                        name = "본인 위시 아닌 항목 포함",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.FORBIDDEN,
                                detail = "해당 위시 아이템에 접근할 권한이 없습니다.",
                            ),
                    )
                    unauthorized()
                }
            }
            if (handlerMethod.binds(WishlistController::registerFromImages)) {
                operation.examples(openApiObjectMapper.delegate) {
                    add(
                        status = HttpStatus.CREATED,
                        name = "이미지 등록 접수 (PROCESSING, 다건)",
                        payload = ApiResponseBody.created(imageProcessingEntries),
                    )
                    add(
                        status = HttpStatus.BAD_REQUEST,
                        name = "이미지 개수 위반 (1~5개 아님)",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.INVALID_INPUT,
                                detail = "이미지는 최소 1개, 최대 5개까지 전송할 수 있습니다.",
                            ),
                    )
                    add(
                        status = HttpStatus.BAD_REQUEST,
                        name = "지원하지 않는 이미지 형식",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.INVALID_INPUT,
                                detail = ProductImage.unsupportedMimeTypeMessage("image/gif"),
                            ),
                    )
                    unauthorized()
                    forbidden("권한 없음 (MEMBER 필요)")
                }
            }
            operation
        }

    // 파싱이 끝난 완성 항목 (READY).
    private val sampleEntry =
        WishItemResponse(
            wish =
                WishItemResponse.WishView(
                    id = 1024,
                    createdAt = LocalDateTime.of(2026, 5, 21, 10, 0, 0),
                ),
            item =
                WishItemResponse.ItemView(
                    id = 512,
                    status = ItemStatus.READY,
                    name = "에어 조던 1 미드",
                    currentPrice = 119_000,
                    currency = "KRW",
                    imageUrl = "https://cdn.example.com/p/512.jpg",
                    sourceUrl = "https://www.example-shop.com/products/12345",
                ),
        )

    // 등록 직후 파싱 중 항목 (PROCESSING) — link 만 있고 name·가격·이미지는 아직 비어 있다.
    private val processingSampleEntry =
        WishItemResponse(
            wish =
                WishItemResponse.WishView(
                    id = 1026,
                    createdAt = LocalDateTime.of(2026, 5, 21, 10, 10, 0),
                ),
            item =
                WishItemResponse.ItemView(
                    id = 514,
                    status = ItemStatus.PROCESSING,
                    name = null,
                    currentPrice = null,
                    currency = null,
                    imageUrl = null,
                    sourceUrl = "https://www.example-shop.com/products/67890",
                ),
        )

    // 이미지 등록 직후 항목들 — link 도 없는 PROCESSING(sourceUrl=null). 비동기 추출이 끝나면 name·가격·이미지가 채워진다.
    private val imageProcessingEntries =
        listOf(
            WishItemResponse(
                wish =
                    WishItemResponse.WishView(
                        id = 1025,
                        createdAt = LocalDateTime.of(2026, 5, 21, 10, 5, 0),
                    ),
                item =
                    WishItemResponse.ItemView(
                        id = 513,
                        status = ItemStatus.PROCESSING,
                        name = null,
                        currentPrice = null,
                        currency = null,
                        imageUrl = null,
                        sourceUrl = null,
                    ),
            ),
            WishItemResponse(
                wish =
                    WishItemResponse.WishView(
                        id = 1027,
                        createdAt = LocalDateTime.of(2026, 5, 21, 10, 5, 0),
                    ),
                item =
                    WishItemResponse.ItemView(
                        id = 515,
                        status = ItemStatus.PROCESSING,
                        name = null,
                        currentPrice = null,
                        currency = null,
                        imageUrl = null,
                        sourceUrl = null,
                    ),
            ),
        )
}
