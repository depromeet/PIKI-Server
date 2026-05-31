package com.depromeet.piki.auth.controller

import com.depromeet.piki.auth.controller.dto.OAuthLoginResponse
import com.depromeet.piki.auth.service.dto.TokenPair
import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.openapi.OpenApiObjectMapper
import com.depromeet.piki.common.openapi.binds
import com.depromeet.piki.common.openapi.examples
import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.user.controller.dto.UserResponse
import com.depromeet.piki.user.domain.IdentityType
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import java.util.UUID

@Configuration
class OAuthApiExamples(
    private val openApiObjectMapper: OpenApiObjectMapper,
) {
    @Bean
    fun oAuthOpenApiExamples(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            if (handlerMethod.binds(OAuthController::login)) {
                operation.examples(openApiObjectMapper.delegate) {
                    add(
                        status = HttpStatus.OK,
                        name = "로그인/가입 성공 (app — body 토큰)",
                        payload =
                            ApiResponseBody.ok(
                                OAuthLoginResponse(
                                    user =
                                        UserResponse(
                                            id = UUID.fromString("8f1a3c2b-9d44-4e2a-9b12-1a2b3c4d5e6f"),
                                            nickname = "뛰어다니는 강아지",
                                            profileImage = "https://lh3.googleusercontent.com/profile.jpg",
                                            identityType = IdentityType.MEMBER,
                                        ),
                                    tokenPair =
                                        TokenPair(
                                            accessToken = "eyJhbGciOiJIUzI1NiJ9.access",
                                            refreshToken = "eyJhbGciOiJIUzI1NiJ9.refresh",
                                        ),
                                ),
                            ),
                    )
                    add(
                        status = HttpStatus.BAD_REQUEST,
                        name = "잘못된 요청 (자격증명 누락 또는 미지원 provider)",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.INVALID_INPUT,
                                status = HttpStatus.BAD_REQUEST,
                                detail = "소셜 로그인 요청이 올바르지 않습니다 (code+redirectUri 또는 accessToken 이 필요합니다).",
                            ),
                    )
                    add(
                        status = HttpStatus.BAD_GATEWAY,
                        name = "소셜 제공자 호출 실패",
                        payload =
                            ApiResponseBody.fail<Unit>(
                                category = ErrorCategory.RETRYABLE,
                                status = HttpStatus.BAD_GATEWAY,
                                detail = "소셜 로그인 제공자 호출에 실패했습니다.",
                            ),
                    )
                }
            }
            operation
        }
}
