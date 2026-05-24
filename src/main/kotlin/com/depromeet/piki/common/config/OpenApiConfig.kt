package com.depromeet.piki.common.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun openAPI(
        @Value("\${spring.application.name:PIKI}") applicationName: String,
    ): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("$applicationName API")
                    // API 버전은 빌드 버전(0.0.1-SNAPSHOT)과 별개의 public 계약 버전이므로 별도 관리
                    // project.version은 Gradle 컨텍스트 전용이라 Spring Bean에서 직접 참조 불가
                    .version("v1")
                    .description(
                        """
                        피키(PIKI) 위시리스트 서비스 API 문서.

                        - 모든 응답의 에러 포맷은 RFC 7807 `application/problem+json` 을 따른다.
                        - 게스트 식별자는 클라이언트가 발급받아 보관하며, 위시리스트 등록 시 함께 전달한다.
                        """.trimIndent(),
                    ).contact(Contact().name("PIKI").url("https://github.com/depromeet/PIKI-Server"))
                    .license(License().name("Apache-2.0").url("https://www.apache.org/licenses/LICENSE-2.0")),
            ).addServersItem(Server().url("/").description("Current host"))
}
