package com.depromeet.team3.user.controller

import com.depromeet.team3.common.exception.ErrorCategory
import com.depromeet.team3.common.openapi.OpenApiObjectMapper
import com.depromeet.team3.common.openapi.binds
import com.depromeet.team3.common.openapi.examples
import com.depromeet.team3.common.response.ApiResponseBody
import com.depromeet.team3.user.controller.dto.NicknameCheckResponse
import com.depromeet.team3.user.controller.dto.UserResponse
import com.depromeet.team3.user.domain.IdentityType
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import java.util.UUID

@Configuration
class UserApiExamples(
    private val openApiObjectMapper: OpenApiObjectMapper,
) {
    @Bean
    fun userOpenApiExamples(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            when {
                handlerMethod.binds(UserController::getMe) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "내 정보 조회 성공",
                            payload = ApiResponseBody.ok(sampleUser()),
                        )
                        add(
                            status = HttpStatus.UNAUTHORIZED,
                            name = "인증 필요",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.UNAUTHORIZED,
                                    status = HttpStatus.UNAUTHORIZED,
                                ),
                        )
                    }

                handlerMethod.binds(UserController::updateMe) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "닉네임 수정 성공",
                            payload = ApiResponseBody.ok(sampleUser().copy(nickname = "새닉네임")),
                        )
                        add(
                            status = HttpStatus.BAD_REQUEST,
                            name = "닉네임 길이/공백 검증 실패",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.INVALID_INPUT,
                                    status = HttpStatus.BAD_REQUEST,
                                    detail = "닉네임은 1자 이상 10자 이하여야 한다.",
                                ),
                        )
                        add(
                            status = HttpStatus.CONFLICT,
                            name = "닉네임 중복",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.CONFLICT,
                                    status = HttpStatus.CONFLICT,
                                    detail = "이미 사용 중인 닉네임입니다.",
                                ),
                        )
                    }

                handlerMethod.binds(UserController::checkNickname) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "사용 가능",
                            payload = ApiResponseBody.ok(NicknameCheckResponse(available = true)),
                        )
                        add(
                            status = HttpStatus.OK,
                            name = "이미 사용 중",
                            payload = ApiResponseBody.ok(NicknameCheckResponse(available = false)),
                        )
                        add(
                            status = HttpStatus.BAD_REQUEST,
                            name = "닉네임 형식 검증 실패",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.INVALID_INPUT,
                                    status = HttpStatus.BAD_REQUEST,
                                    detail = "nickname 은 10자 이하여야 한다.",
                                ),
                        )
                    }
            }
            operation
        }

    private fun sampleUser(): UserResponse =
        UserResponse(
            id = UUID.fromString("8f1a3c2b-9d44-4e2a-9b12-1a2b3c4d5e6f"),
            nickname = "뛰어다니는 강아지",
            profileImage = "https://api.dicebear.com/9.x/bottts/svg?seed=8f1a3c2b-9d44-4e2a-9b12-1a2b3c4d5e6f",
            identityType = IdentityType.GUEST,
        )
}
