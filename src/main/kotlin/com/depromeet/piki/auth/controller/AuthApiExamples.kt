package com.depromeet.piki.auth.controller

import com.depromeet.piki.auth.controller.dto.GuestCreateResponse
import com.depromeet.piki.auth.controller.dto.LogoutResponse
import com.depromeet.piki.auth.controller.dto.TokenRefreshResponse
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
                            name = "게스트 생성 성공 (APP — body 토큰)",
                            payload =
                                ApiResponseBody.created(
                                    GuestCreateResponse(
                                        user =
                                            UserResponse(
                                                id = UUID.fromString("8f1a3c2b-9d44-4e2a-9b12-1a2b3c4d5e6f"),
                                                nickname = "뛰어다니는 강아지",
                                                profileImage =
                                                    "https://api.dicebear.com/9.x/bottts/svg?seed=8f1a3c2b-9d44-4e2a-9b12-1a2b3c4d5e6f",
                                                identityType = IdentityType.GUEST,
                                            ),
                                        tokenPair =
                                            TokenPair(
                                                accessToken = "eyJhbGciOiJIUzI1NiJ9.access",
                                                refreshToken = "eyJhbGciOiJIUzI1NiJ9.refresh",
                                            ),
                                    ),
                                ),
                        )
                    }

                handlerMethod.binds(AuthController::refresh) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "토큰 갱신 성공 (APP — body 토큰)",
                            payload =
                                ApiResponseBody.ok(
                                    TokenRefreshResponse(
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
                            name = "리프레시 토큰 미입력",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.INVALID_INPUT,
                                    detail = "리프레시 토큰이 필요합니다.",
                                ),
                        )
                        add(
                            status = HttpStatus.UNAUTHORIZED,
                            name = "유효하지 않은 토큰",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.UNAUTHORIZED,
                                    detail = "유효하지 않은 토큰입니다.",
                                ),
                        )
                    }

                handlerMethod.binds(AuthController::logout) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "로그아웃 성공",
                            payload = ApiResponseBody.ok(LogoutResponse()),
                        )
                        add(
                            status = HttpStatus.UNAUTHORIZED,
                            name = "인증 필요",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.UNAUTHORIZED,
                                ),
                        )
                    }

                handlerMethod.binds(DevAuthController::createDevUser) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.CREATED,
                            name = "MEMBER 생성 성공",
                            payload =
                                ApiResponseBody.created(
                                    GuestCreateResponse(
                                        user =
                                            UserResponse(
                                                id = UUID.fromString("3b9c1d2e-4f5a-4b6c-8d7e-9f0a1b2c3d4e"),
                                                nickname = "홍길동",
                                                profileImage =
                                                    "https://api.dicebear.com/9.x/bottts/svg?seed=3b9c1d2e-4f5a-4b6c-8d7e-9f0a1b2c3d4e",
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
                            name = "닉네임 미입력",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.INVALID_INPUT,
                                    detail = "nickname: must not be blank",
                                ),
                        )
                        add(
                            status = HttpStatus.UNAUTHORIZED,
                            name = "인증 필요",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.UNAUTHORIZED,
                                ),
                        )
                        add(
                            status = HttpStatus.FORBIDDEN,
                            name = "GUEST 권한 없음 (MEMBER 토큰으로 호출 불가)",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.FORBIDDEN,
                                ),
                        )
                        add(
                            status = HttpStatus.CONFLICT,
                            name = "이미 사용 중인 닉네임",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.CONFLICT,
                                    detail = "이미 사용 중인 닉네임입니다.",
                                ),
                        )
                    }

                handlerMethod.binds(DevAuthController::issueTokenForUser) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.CREATED,
                            name = "토큰 발급 성공",
                            payload =
                                ApiResponseBody.created(
                                    GuestCreateResponse(
                                        user =
                                            UserResponse(
                                                id = UUID.fromString("3b9c1d2e-4f5a-4b6c-8d7e-9f0a1b2c3d4e"),
                                                nickname = "홍길동",
                                                profileImage =
                                                    "https://api.dicebear.com/9.x/bottts/svg?seed=3b9c1d2e-4f5a-4b6c-8d7e-9f0a1b2c3d4e",
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
                            status = HttpStatus.NOT_FOUND,
                            name = "userId 에 해당하는 user 없음",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.NOT_FOUND,
                                ),
                        )
                        add(
                            status = HttpStatus.CONFLICT,
                            name = "탈퇴된 user",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.CONFLICT,
                                ),
                        )
                        add(
                            status = HttpStatus.UNAUTHORIZED,
                            name = "인증 필요",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.UNAUTHORIZED,
                                ),
                        )
                        add(
                            status = HttpStatus.FORBIDDEN,
                            name = "GUEST 권한 없음 (MEMBER 토큰으로 호출 불가)",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.FORBIDDEN,
                                ),
                        )
                    }
            }
            operation
        }
}
