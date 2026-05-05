package com.depromeet.team3.wishlist.controller

import com.depromeet.team3.common.exception.ErrorCategory
import com.depromeet.team3.common.openapi.OpenApiObjectMapper
import com.depromeet.team3.common.openapi.binds
import com.depromeet.team3.common.openapi.examples
import com.depromeet.team3.common.response.ApiResponseBody
import com.depromeet.team3.wishlist.controller.dto.WishlistRegisterResponse
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus

@Configuration
class WishlistApiExamples(private val openApiObjectMapper: OpenApiObjectMapper) {

    @Bean
    fun wishlistOpenApiExamples(): OperationCustomizer = OperationCustomizer { operation, handlerMethod ->
        if (handlerMethod.binds(WishlistController::register)) {
            operation.examples(openApiObjectMapper.delegate) {
                add(
                    status = HttpStatus.CREATED,
                    name = "등록 성공",
                    payload = ApiResponseBody.created(
                        WishlistRegisterResponse(
                            wishId = 1024,
                            name = "에어 조던 1 미드",
                            regularPrice = 159_000,
                            discountedPrice = 119_000,
                            discountRate = 25,
                            currency = "KRW",
                            imageUrl = "https://cdn.example.com/p/1024.jpg",
                        ),
                    ),
                )
                add(
                    status = HttpStatus.BAD_REQUEST,
                    name = "URL 형식 오류",
                    payload = ApiResponseBody.fail<Unit>(
                        category = ErrorCategory.INVALID_INPUT,
                        status = HttpStatus.BAD_REQUEST,
                        detail = "지원하지 않는 URL 형식입니다.",
                    ),
                )
                add(
                    status = HttpStatus.CONFLICT,
                    name = "중복 등록",
                    payload = ApiResponseBody.fail<Unit>(
                        category = ErrorCategory.CONFLICT,
                        status = HttpStatus.CONFLICT,
                        detail = "이미 위시리스트에 등록된 상품입니다.",
                    ),
                )
            }
        }
        operation
    }
}
