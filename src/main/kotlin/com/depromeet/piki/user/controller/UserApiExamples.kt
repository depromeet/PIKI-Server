package com.depromeet.piki.user.controller

import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.openapi.OpenApiObjectMapper
import com.depromeet.piki.common.openapi.binds
import com.depromeet.piki.common.openapi.examples
import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.user.controller.dto.NicknameCheckRequest
import com.depromeet.piki.user.controller.dto.NicknameCheckResponse
import com.depromeet.piki.user.controller.dto.UserResponse
import com.depromeet.piki.user.controller.dto.UserUpdateRequest
import com.depromeet.piki.user.domain.IdentityType
import com.depromeet.piki.user.domain.UserException
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
                        unauthorized()
                        add(
                            status = HttpStatus.NOT_FOUND,
                            name = "유저 없음 (JWT 유효하나 DB에 없음)",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.NOT_FOUND,
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
                                    // @RequestBody Bean Validation 위반은 GlobalExceptionHandler.detailOf 가 "필드명: 메시지" 로 만든다.
                                    detail = "nickname: ${UserUpdateRequest.NICKNAME_SIZE_MESSAGE}",
                                ),
                        )
                        add(UserException.duplicateNickname(), name = "닉네임 중복")
                        unauthorized()
                        add(
                            status = HttpStatus.NOT_FOUND,
                            name = "유저 없음 (JWT 유효하나 DB에 없음)",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.NOT_FOUND,
                                ),
                        )
                    }

                handlerMethod.binds(UserController::withdraw) ->
                    operation.examples(openApiObjectMapper.delegate) {
                        add(
                            status = HttpStatus.OK,
                            name = "탈퇴 성공",
                            payload = ApiResponseBody.ok<Unit>(),
                        )
                        unauthorized()
                        add(UserException.guestCannotWithdraw(), name = "게스트 탈퇴 거부")
                        add(
                            status = HttpStatus.NOT_FOUND,
                            name = "유저 없음 (JWT 유효하나 DB에 없음)",
                            payload =
                                ApiResponseBody.fail<Unit>(
                                    category = ErrorCategory.NOT_FOUND,
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
                                    // 쿼리 파라미터 바인딩 @Valid 위반도 GlobalExceptionHandler.detailOf 가 "필드명: 메시지" 로 만든다.
                                    detail = "nickname: ${NicknameCheckRequest.NICKNAME_SIZE_MESSAGE}",
                                ),
                        )
                        unauthorized()
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
