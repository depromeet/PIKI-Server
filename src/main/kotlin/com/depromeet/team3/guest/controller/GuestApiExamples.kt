package com.depromeet.team3.guest.controller

import com.depromeet.team3.common.openapi.OpenApiObjectMapper
import com.depromeet.team3.common.openapi.binds
import com.depromeet.team3.common.openapi.examples
import com.depromeet.team3.common.response.ApiResponseBody
import com.depromeet.team3.guest.controller.dto.GuestResponse
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import java.util.UUID

@Configuration
class GuestApiExamples(private val openApiObjectMapper: OpenApiObjectMapper) {

    @Bean
    fun guestOpenApiExamples(): OperationCustomizer = OperationCustomizer { operation, handlerMethod ->
        if (handlerMethod.binds(GuestController::issueGuestId)) {
            operation.examples(openApiObjectMapper.delegate) {
                add(
                    status = HttpStatus.OK,
                    name = "발급 성공",
                    payload = ApiResponseBody.ok(
                        GuestResponse(UUID.fromString("8f1a3c2b-9d44-4e2a-9b12-1a2b3c4d5e6f")),
                    ),
                )
            }
        }
        operation
    }
}
