package com.depromeet.piki.auth.controller

import com.depromeet.piki.auth.infrastructure.oauth.apple.AppleNotificationException
import com.depromeet.piki.common.openapi.OpenApiObjectMapper
import com.depromeet.piki.common.openapi.binds
import com.depromeet.piki.common.openapi.examples
import com.depromeet.piki.common.response.ApiResponseBody
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus

@Configuration
class AppleNotificationApiExamples(
    private val openApiObjectMapper: OpenApiObjectMapper,
) {
    @Bean
    fun appleNotificationOpenApiExamples(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            if (handlerMethod.binds(AppleNotificationController::handle)) {
                operation.examples(openApiObjectMapper.delegate) {
                    add(
                        status = HttpStatus.OK,
                        name = "알림 처리 완료 (멱등)",
                        payload = ApiResponseBody.ok<Unit>(),
                    )
                    // 서명/issuer/aud 검증 실패 → 401. detail·category 를 예외 정의에서 자동 추출(손으로 박지 않음).
                    add(AppleNotificationException.invalidSignature(), name = "유효하지 않은 Apple 알림")
                }
            }
            operation
        }
}
