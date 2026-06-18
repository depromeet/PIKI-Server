package com.depromeet.piki.common.imageproxy

import com.depromeet.piki.common.openapi.OpenApiObjectMapper
import com.depromeet.piki.common.openapi.binds
import com.depromeet.piki.common.openapi.examples
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ImageProxyApiExamples(
    private val openApiObjectMapper: OpenApiObjectMapper,
) {
    @Bean
    fun imageProxyOpenApiExamples(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            if (handlerMethod.binds(ImageProxyController::proxyImage)) {
                operation.examples(openApiObjectMapper.delegate) {
                    add(ImageProxyException.blockedDomain(), name = "허용되지 않은 도메인")
                    add(ImageProxyException.imageTooLarge(), name = "이미지 크기 초과 (5 MB)")
                    unauthorized()
                    add(ImageProxyException.fetchFailed(), name = "외부 이미지 서버 오류")
                }
            }
            operation
        }
}
