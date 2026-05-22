package com.depromeet.team3.wishlist.controller

import com.depromeet.team3.common.exception.ErrorCategory
import com.depromeet.team3.common.openapi.OpenApiObjectMapper
import com.depromeet.team3.common.openapi.binds
import com.depromeet.team3.common.openapi.examples
import com.depromeet.team3.common.response.ApiResponseBody
import com.depromeet.team3.common.response.PageResponse
import com.depromeet.team3.wishlist.controller.dto.WishItemResponse
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
                        name = "등록 성공",
                        payload = ApiResponseBody.created(sampleEntry),
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
            operation
        }

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
                    name = "에어 조던 1 미드",
                    currentPrice = 119_000,
                    currency = "KRW",
                    imageUrl = "https://cdn.example.com/p/512.jpg",
                    sourceUrl = "https://www.example-shop.com/products/12345",
                ),
        )
}
