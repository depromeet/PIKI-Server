package com.depromeet.team3.common.openapi

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class OpenApiObjectMapper(val delegate: ObjectMapper)

@Configuration
class OpenApiObjectMapperConfig {

    // IntelliJ Spring 플러그인이 JacksonAutoConfiguration이 등록한 ObjectMapper 빈을 인식 못해
    // false positive 경고를 띄운다. 런타임 주입은 정상이며, 회피 어노테이션은 이 한 지점에만 둔다.
    @Bean
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    fun openApiObjectMapper(objectMapper: ObjectMapper): OpenApiObjectMapper =
        OpenApiObjectMapper(objectMapper)
}
