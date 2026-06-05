package com.depromeet.piki.notification.fcm.controller

import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.openapi.OpenApiObjectMapper
import com.depromeet.piki.common.openapi.binds
import com.depromeet.piki.common.openapi.examples
import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.notification.fcm.controller.dto.DevPushResponse
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus

@Configuration
class DevFcmApiExamples(
    private val openApiObjectMapper: OpenApiObjectMapper,
) {
    @Bean
    fun devFcmOpenApiExamples(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            if (handlerMethod.binds(DevFcmController::push)) {
                operation.examples(openApiObjectMapper.delegate) {
                    add(
                        status = HttpStatus.OK,
                        name = "발송 위임 성공",
                        payload = ApiResponseBody.ok(DevPushResponse(targetTokenCount = 2, fcmEnabled = true)),
                    )
                    add(
                        status = HttpStatus.BAD_REQUEST,
                        name = "제목 또는 본문 길이 초과",
                        payload =
                            ApiResponseBody.fail<DevPushResponse>(
                                category = ErrorCategory.INVALID_INPUT,
                                detail = "푸시 제목과 본문은 255자를 초과할 수 없습니다.",
                            ),
                    )
                    unauthorized()
                    forbidden(name = "GUEST 권한 없음")
                }
            }
            operation
        }
}
