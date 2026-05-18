package com.depromeet.team3.auth.controller

import com.depromeet.team3.auth.controller.dto.GuestCreateResponse
import com.depromeet.team3.auth.controller.dto.TokenRefreshResponse
import com.depromeet.team3.common.exception.ErrorCategory
import com.depromeet.team3.common.openapi.OpenApiObjectMapper
import com.depromeet.team3.common.openapi.binds
import com.depromeet.team3.common.openapi.examples
import com.depromeet.team3.common.response.ApiResponseBody
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus

@Configuration
class AuthApiExamples(
    private val openApiObjectMapper: OpenApiObjectMapper,
) {
    @Bean
    fun authOpenApiExamples(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            when {
                handlerMethod.binds(AuthController::createGuest) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.CREATED,
                            name = "게스트 생성 성공",
                            payload =
                                ApiResponseBody.created(
                                    GuestCreateResponse(
                                        accessToken = "eyJhbGciOiJIUzUxMiJ9.access...",
                                        refreshToken = "eyJhbGciOiJIUzUxMiJ9.refresh...",
                                    ),
                                ),
                        )
                    }

                handlerMethod.binds(AuthController::refresh) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "토큰 갱신 성공",
                            payload =
                                ApiResponseBody.ok(
                                    TokenRefreshResponse(
                                        accessToken = "eyJhbGciOiJIUzUxMiJ9.access...",
                                        refreshToken = "eyJhbGciOiJIUzUxMiJ9.refresh...",
                                    ),
                                ),
                        )
                        add(
                            status = HttpStatus.UNAUTHORIZED,
                            name = "유효하지 않은 토큰",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.UNAUTHORIZED,
                                    status = HttpStatus.UNAUTHORIZED,
                                    detail = "유효하지 않은 토큰입니다.",
                                ),
                        )
                    }

                handlerMethod.binds(AuthController::logout) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "로그아웃 성공",
                            payload = ApiResponseBody.ok<Nothing>(),
                        )
                        add(
                            status = HttpStatus.UNAUTHORIZED,
                            name = "인증 필요",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.UNAUTHORIZED,
                                    status = HttpStatus.UNAUTHORIZED,
                                    detail = "인증이 필요합니다.",
                                ),
                        )
                    }
            }
            operation
        }
}
