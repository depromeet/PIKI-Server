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

                        - 모든 응답은 공통 래퍼(`ApiResponseBody`)로 `application/json` 으로 내려간다. 필드는 `status`·`code`·`detail`·`data`·`pageResponse` 이며, 성공·실패가 같은 형태다. 실패 시 `data` 는 null 이고 `code`·`detail` 에 사유가 담긴다.
                        - 인증은 JWT 기반이다. 게스트 생성 또는 소셜 로그인으로 액세스·리프레시 토큰 쌍을 발급받고, 보호된 API 는 액세스 토큰을 `Authorization: Bearer {accessToken}` 헤더로 전달한다. 만료 시 리프레시 토큰으로 갱신한다.
                        """.trimIndent(),
                    ).contact(Contact().name("PIKI").url("https://github.com/depromeet/PIKI-Server"))
                    .license(License().name("Apache-2.0").url("https://www.apache.org/licenses/LICENSE-2.0")),
            ).addServersItem(Server().url("/").description("Current host"))
}
