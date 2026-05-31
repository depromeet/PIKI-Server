package com.depromeet.piki.auth.config

import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.user.domain.IdentityType
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

// Spring Security 필터 체인은 DispatcherServlet 이전이라 GlobalExceptionHandler 가 잡지 못하고,
// 401·403 응답은 EntryPoint·AccessDeniedHandler 에서만 작성된다. 본문이 빈 채로 내려가던 회귀
// (#213) 가 다시 새지 않도록, 필터 단 401·403 응답이 다른 엔드포인트와 같은 ApiResponseBody
// contract (detail 등) 로 내려가는지 한 자리에서 검증한다.
class SecurityErrorResponseIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Test
    fun `토큰 없이 보호 엔드포인트 호출 시 401 응답이 ApiResponseBody contract 로 내려간다`() {
        buildMockMvc()
            .perform(post("/api/v1/wishlists"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.detail", notNullValue()))
    }

    @Test
    fun `위조된 Bearer 토큰으로 보호 엔드포인트 호출 시 401 응답이 ApiResponseBody contract 로 내려간다`() {
        buildMockMvc()
            .perform(
                post("/api/v1/wishlists")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.value"),
            ).andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.detail", notNullValue()))
    }

    @Test
    fun `MEMBER 권한 토큰으로 GUEST 전용 dev 엔드포인트 호출 시 403 응답이 ApiResponseBody contract 로 내려간다`() {
        val memberToken = jwtProvider.generateAccessToken(UUID.randomUUID(), IdentityType.MEMBER)

        buildMockMvc()
            .perform(
                post("/api/v1/dev/users")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $memberToken"),
            ).andExpect(status().isForbidden)
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.detail", notNullValue()))
    }

    private fun buildMockMvc(): MockMvc =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()
}
