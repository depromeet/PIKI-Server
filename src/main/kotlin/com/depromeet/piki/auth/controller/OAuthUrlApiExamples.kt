package com.depromeet.piki.auth.controller

import com.depromeet.piki.auth.controller.dto.OAuthUrlResponse
import com.depromeet.piki.auth.infrastructure.oauth.OAuthException
import com.depromeet.piki.common.openapi.OpenApiObjectMapper
import com.depromeet.piki.common.openapi.binds
import com.depromeet.piki.common.openapi.examples
import com.depromeet.piki.common.response.ApiResponseBody
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus

@Configuration
class OAuthUrlApiExamples(
    private val openApiObjectMapper: OpenApiObjectMapper,
) {
    @Bean
    fun oAuthUrlOpenApiExamples(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            if (handlerMethod.binds(OAuthUrlController::getAuthUrl)) {
                operation.examples(openApiObjectMapper.delegate) {
                    add(
                        status = HttpStatus.OK,
                        name = "카카오 인가 URL 생성 성공",
                        payload =
                            ApiResponseBody.ok(
                                OAuthUrlResponse(
                                    url =
                                        "https://kauth.kakao.com/oauth/authorize" +
                                            "?client_id=kakao-client-id" +
                                            "&redirect_uri=https%3A%2F%2Fapp.example.com%2Fcallback%2Fkakao" +
                                            "&response_type=code" +
                                            "&state=550e8400-e29b-41d4-a716-446655440000",
                                    state = "550e8400-e29b-41d4-a716-446655440000",
                                ),
                            ),
                    )
                    add(OAuthException.unsupportedProvider(), name = "지원하지 않는 provider")
                }
            }
            operation
        }
}
