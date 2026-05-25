package com.depromeet.piki.wishlist.controller

import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.openapi.OpenApiObjectMapper
import com.depromeet.piki.common.openapi.binds
import com.depromeet.piki.common.openapi.examples
import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.common.response.PageResponse
import com.depromeet.piki.item.domain.ItemStatus
import com.depromeet.piki.ocr.domain.OcrImage
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
            if (handlerMethod.binds(WishlistController::register)) {
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
                                status = HttpStatus.BAD_REQUEST,
                                detail = "지원하지 않는 URL 형식입니다.",
                            ),
                    )
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
                }
            }
            if (handlerMethod.binds(WishlistController::updateWish)) {
                operation.examples(openApiObjectMapper.delegate) {
                    add(
                        status = HttpStatus.OK,
                        name = "수정 성공",
                        payload = ApiResponseBody.ok(sampleEntry),
                    )
                    add(
                        status = HttpStatus.BAD_REQUEST,
                        name = "가격 음수",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.INVALID_INPUT,
                                status = HttpStatus.BAD_REQUEST,
                                detail = "가격은 0 이상이어야 합니다.",
                            ),
                    )
                    add(
                        status = HttpStatus.FORBIDDEN,
                        name = "본인 위시 아님",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.FORBIDDEN,
                                status = HttpStatus.FORBIDDEN,
                                detail = "해당 위시 아이템에 접근할 권한이 없습니다.",
                            ),
                    )
                    add(
                        status = HttpStatus.NOT_FOUND,
                        name = "존재하지 않는 위시 항목",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.NOT_FOUND,
                                status = HttpStatus.NOT_FOUND,
                                detail = "존재하지 않는 위시리스트 항목입니다.",
                            ),
                    )
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
                                status = HttpStatus.FORBIDDEN,
                                detail = "해당 위시 아이템에 접근할 권한이 없습니다.",
                            ),
                    )
                    add(
                        status = HttpStatus.NOT_FOUND,
                        name = "존재하지 않는 위시 항목",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.NOT_FOUND,
                                status = HttpStatus.NOT_FOUND,
                                detail = "존재하지 않는 위시리스트 항목입니다.",
                            ),
                    )
                }
            }
            if (handlerMethod.binds(WishlistController::registerFromOcr)) {
                operation.examples(openApiObjectMapper.delegate) {
                    add(
                        status = HttpStatus.CREATED,
                        name = "OCR 등록 성공 (URL 없음)",
                        payload = ApiResponseBody.created(ocrSampleEntry),
                    )
                    add(
                        status = HttpStatus.BAD_REQUEST,
                        name = "지원하지 않는 이미지 형식",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.INVALID_INPUT,
                                status = HttpStatus.BAD_REQUEST,
                                detail = OcrImage.unsupportedMimeTypeMessage("image/gif"),
                            ),
                    )
                    add(
                        status = HttpStatus.BAD_GATEWAY,
                        name = "Gemini 호출 실패",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.RETRYABLE,
                                status = HttpStatus.BAD_GATEWAY,
                                detail = "Gemini 호출 실패",
                            ),
                    )
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

    // OCR 등록 항목 — URL·이미지·통화가 없어 해당 필드가 null 이다. 동기 완성이라 READY.
    private val ocrSampleEntry =
        WishItemResponse(
            wish =
                WishItemResponse.WishView(
                    id = 1025,
                    createdAt = LocalDateTime.of(2026, 5, 21, 10, 5, 0),
                ),
            item =
                WishItemResponse.ItemView(
                    id = 513,
                    status = ItemStatus.READY,
                    name = "에어 조던 1 미드",
                    currentPrice = 119_000,
                    currency = null,
                    imageUrl = null,
                    sourceUrl = null,
                ),
        )
}
