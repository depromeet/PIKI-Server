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
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus

@Profile("!prod")
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
                        name = "발송 성공",
                        payload = ApiResponseBody.ok(DevPushResponse(fcmEnabled = true, staleTokenCount = 0)),
                    )
                    add(
                        status = HttpStatus.OK,
                        name = "FCM 미설정 (no-op)",
                        payload = ApiResponseBody.ok(DevPushResponse(fcmEnabled = false, staleTokenCount = 0)),
                    )
                    add(
                        status = HttpStatus.BAD_REQUEST,
                        name = "토큰 누락",
                        payload =
                            ApiResponseBody.fail<DevPushResponse>(
                                category = ErrorCategory.INVALID_INPUT,
                                detail = "FCM 토큰은 비어 있을 수 없습니다.",
                            ),
                    )
                    unauthorized()
                    forbidden(name = "GUEST 권한 없음")
                }
            }
            operation
        }
}
