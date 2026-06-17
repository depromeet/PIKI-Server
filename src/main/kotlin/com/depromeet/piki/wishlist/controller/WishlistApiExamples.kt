package com.depromeet.piki.wishlist.controller

import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.openapi.OpenApiObjectMapper
import com.depromeet.piki.common.openapi.binds
import com.depromeet.piki.common.openapi.examples
import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.common.response.PageResponse
import com.depromeet.piki.common.storage.ImageStorageException
import com.depromeet.piki.image.domain.ProductImageException
import com.depromeet.piki.item.domain.ItemException
import com.depromeet.piki.item.domain.ItemStatus
import com.depromeet.piki.product.domain.ProductLinkException
import com.depromeet.piki.wishlist.controller.dto.WishItemResponse
import com.depromeet.piki.wishlist.controller.dto.WishlistUpdateRequest
import com.depromeet.piki.wishlist.domain.WishException
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
                        name = "등록 접수 (파싱 대기 — PENDING)",
                        payload = ApiResponseBody.created(pendingSampleEntry),
                    )
                    add(ProductLinkException.invalidFormat(urlFormatCause), name = "유효하지 않은 URL 형식")
                    add(ProductLinkException.unsupportedScheme(), name = "https 외 스킴")
                    add(ProductLinkException.unsupportedPlatform(), name = "지원하지 않는 쇼핑몰 (KREAM·쿠팡·네이버)")
                    unauthorized()
                    forbidden("권한 없음 (MEMBER 필요)")
                }
            }
            if (handlerMethod.binds(WishlistController::getWishlist)) {
                operation.examples(openApiObjectMapper.delegate) {
                    add(
                        status = HttpStatus.OK,
                        name = "조회 성공 (대기·담는 중 + 완성 혼재, 마지막 페이지)",
                        payload =
                            ApiResponseBody.ok(
                                data = listOf(pendingSampleEntry, processingSampleEntry, sampleEntry),
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
                    add(WishException.invalidCursor(), name = "유효하지 않은 cursor")
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
                    add(WishException.forbiddenWishItems(), name = "본인 위시 아님")
                    add(WishException.notFound(), name = "존재하지 않는 위시 항목")
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
                                // @ModelAttribute Bean Validation 위반은 GlobalExceptionHandler.detailOf 가 위반 필드의 메시지를 그대로 detail 로 내린다.
                                detail = WishlistUpdateRequest.PRICE_MIN_MESSAGE,
                            ),
                    )
                    add(ItemException.nameRequiredForReady(), name = "상품명 없이 복구 시도")
                    add(WishException.forbiddenWishItems(), name = "본인 위시 아님")
                    add(WishException.notFound(), name = "존재하지 않는 위시 항목")
                    add(ItemException.alreadyReady(), name = "이미 등록 완료(READY) 항목 — 수정 불가")
                    add(ItemException.stillProcessing(), name = "아직 대기·처리 중(PENDING·PROCESSING) 항목 — 수정 불가")
                    add(ImageStorageException.uploadFailed(), name = "이미지 저장 실패")
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
                    add(WishException.forbiddenWishItems(), name = "본인 위시 아님")
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
                    add(WishException.invalidIdCount(), name = "ids 누락/빈 목록/100개 초과")
                    add(WishException.forbiddenWishItems(), name = "본인 위시 아닌 항목 포함")
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
                    add(WishException.invalidImageCount(), name = "이미지 개수 위반 (1~5개 아님)")
                    add(ProductImageException.unsupportedType(), name = "지원하지 않는 이미지 형식")
                    unauthorized()
                    forbidden("권한 없음 (MEMBER 필요)")
                }
            }
            operation
        }

    // ProductLinkException.invalidFormat 은 cause 를 요구하지만, example 헬퍼는 message·category·status 만
    // 사용한다(GlobalExceptionHandler.handleBaseException 과 동일). 따라서 이 cause 는 payload 에 영향을 주지 않는 더미다.
    private val urlFormatCause = IllegalArgumentException("example")

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

    // URL 등록 직후 항목 (PENDING) — link 만 있고 name·가격·이미지는 비어 있다. 디스패처가 집어 PROCESSING 으로 전이한다.
    private val pendingSampleEntry =
        WishItemResponse(
            wish =
                WishItemResponse.WishView(
                    id = 1027,
                    createdAt = LocalDateTime.of(2026, 5, 21, 10, 11, 0),
                ),
            item =
                WishItemResponse.ItemView(
                    id = 515,
                    status = ItemStatus.PENDING,
                    name = null,
                    currentPrice = null,
                    currency = null,
                    imageUrl = null,
                    sourceUrl = "https://www.example-shop.com/products/67891",
                ),
        )

    // 파싱 진행 중 항목 (PROCESSING) — 디스패처가 집어 추출 중인 상태. 목록·단건 조회에서 등장한다.
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
