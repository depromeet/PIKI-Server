package com.depromeet.piki.common.controller

import com.depromeet.piki.support.IntegrationTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

class DocsAccessIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Test
    fun `GET docs index html - 인증 없이 200 응답이 와야 한다 (Stoplight UI 접근 회귀 가드)`() {
        // anyRequest().authenticated() 가 적용된 상태에서 /docs/** 가 permitAll 명시 누락 시
        // 401 응답으로 운영 docs UI (https://api.depromeet18team3.cloud/docs/index.html) 가 차단된다.
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        mockMvc
            .perform(get("/docs/index.html"))
            .andExpect(status().isOk)
    }

    @Test
    fun `GET v3 api-docs - 인증 없이 200 응답이 와야 한다 (Stoplight 가 fetch 하는 OpenAPI spec)`() {
        // Stoplight UI 가 동작하려면 spec endpoint 도 인증 없이 접근 가능해야 한다.
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
    }

    @Test
    fun `GET v3 api-docs - JWT Bearer SecurityScheme 가 스펙에 선언되어 있다`() {
        // 이 선언이 없으면 문서 UI 가 "인증 필요" 를 알 수 없어 토큰 입력란·Authorization 헤더 cURL
        // 샘플을 그리지 못한다 (Security 필터 규칙은 springdoc 이 모름). 회귀 가드.
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
            // 최상위 글로벌 security 로 모든 엔드포인트에 기본 Bearer 요구가 걸린다.
            .andExpect(jsonPath("$.security[0].bearerAuth").exists())
    }

    @Test
    fun `GET v3 api-docs - public 은 인증 해제되고 보호 엔드포인트는 글로벌 Bearer 를 상속한다`() {
        // 글로벌 Bearer 요구가 게스트 생성 같은 진입점까지 자물쇠로 덮으면 문서가 거짓이 된다.
        // @SecurityRequirements(빈) 으로 public operation 의 security 가 빈 배열이 되는지,
        // 인증 필요한 dev/users 는 operation 에 security 명시 없이 글로벌을 상속하는지 회귀 가드.
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            // public: @SecurityRequirements(빈) → operation security 가 빈 배열로 글로벌 요구를 끊는다.
            .andExpect(jsonPath("$.paths['/api/v1/auth/guest'].post.security").isArray)
            .andExpect(jsonPath("$.paths['/api/v1/auth/guest'].post.security").isEmpty)
            // 보호: operation 에 security 명시 없음 → 최상위 글로벌 Bearer 상속 (문서 UI 에 자물쇠 표시).
            .andExpect(jsonPath("$.paths['/api/v1/dev/users'].post.security").doesNotExist())
    }
}
