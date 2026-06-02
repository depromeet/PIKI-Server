package com.depromeet.piki.auth.controller

import com.depromeet.piki.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.piki.auth.infrastructure.oauth.OAuthUserInfo
import com.depromeet.piki.auth.infrastructure.redis.OAuthStateStore
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.StubOAuthClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper

@Transactional
class OAuthUrlIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    @Qualifier("googleOAuthClient")
    private lateinit var googleOAuthClient: StubOAuthClient

    @Autowired
    private lateinit var oAuthStateStore: OAuthStateStore

    private fun mockMvc(): MockMvc =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    @Test
    fun `GET auth url - kakao 인가 URL 과 state 를 반환한다`() {
        mockMvc()
            .perform(get("/api/v1/auth/kakao/url"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.url").isString)
            .andExpect(jsonPath("$.data.state").isString)
    }

    @Test
    fun `GET auth url - google 인가 URL 과 state 를 반환한다`() {
        mockMvc()
            .perform(get("/api/v1/auth/google/url"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.url").isString)
            .andExpect(jsonPath("$.data.state").isString)
    }

    @Test
    fun `GET auth url - 미지원 provider 는 400`() {
        mockMvc()
            .perform(get("/api/v1/auth/apple/url"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `state 검증 - 발급된 state 로 로그인하면 200 성공한다`() {
        val state = extractState(
            mockMvc()
                .perform(get("/api/v1/auth/google/url"))
                .andReturn()
                .response.contentAsString,
        )
        googleOAuthClient.fetchByCodeStub = { _, _ -> OAuthUserInfo(OAuthProvider.GOOGLE, "google_state_ok", null) }

        mockMvc()
            .perform(
                post("/api/v1/auth/login/google")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Client-Type", "app")
                    .content("""{"code":"auth-code","redirectUri":"https://app/callback","state":"$state"}"""),
            ).andExpect(status().isOk)
    }

    @Test
    fun `state 검증 - 잘못된 state 로 로그인하면 401`() {
        mockMvc()
            .perform(get("/api/v1/auth/google/url"))
            .andExpect(status().isOk)

        mockMvc()
            .perform(
                post("/api/v1/auth/login/google")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Client-Type", "app")
                    .content("""{"code":"auth-code","redirectUri":"https://app/callback","state":"invalid-state-uuid"}"""),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `state 검증 - 동일 state 로 두 번 로그인하면 두 번째는 401`() {
        val state = extractState(
            mockMvc()
                .perform(get("/api/v1/auth/google/url"))
                .andReturn()
                .response.contentAsString,
        )
        googleOAuthClient.fetchByCodeStub = { _, _ -> OAuthUserInfo(OAuthProvider.GOOGLE, "google_state_2nd", null) }
        val loginBody = """{"code":"auth-code","redirectUri":"https://app/callback","state":"$state"}"""

        mockMvc()
            .perform(
                post("/api/v1/auth/login/google")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Client-Type", "app")
                    .content(loginBody),
            ).andExpect(status().isOk)

        mockMvc()
            .perform(
                post("/api/v1/auth/login/google")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Client-Type", "app")
                    .content(loginBody),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `state 없이 로그인 - v2 SDK 흐름은 state 없어도 정상 로그인된다`() {
        googleOAuthClient.fetchByAccessTokenStub = { OAuthUserInfo(OAuthProvider.GOOGLE, "google_no_state", null) }

        mockMvc()
            .perform(
                post("/api/v1/auth/login/google")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Client-Type", "app")
                    .content("""{"accessToken":"sdk-token"}"""),
            ).andExpect(status().isOk)
    }

    @Test
    fun `GET auth url - redirectUri 쿼리 파라미터를 넘기면 반환된 URL 에 해당 값이 반영된다`() {
        val customRedirectUri = "http://localhost:3000/auth/callback/google"

        val response =
            mockMvc()
                .perform(get("/api/v1/auth/google/url").param("redirectUri", customRedirectUri))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.url").isString)
                .andReturn()
                .response.contentAsString

        val url = objectMapper.readTree(response).at("/data/url").asText()
        assert(url.contains(customRedirectUri)) { "반환된 URL 에 customRedirectUri 가 포함되어야 한다: $url" }
    }

    @Test
    fun `GET auth url - redirectUri 생략 시 기본 redirect URI 로 URL 이 생성된다`() {
        mockMvc()
            .perform(get("/api/v1/auth/google/url"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.url").isString)
            .andExpect(jsonPath("$.data.state").isString)
    }

    private fun extractState(responseBody: String): String =
        objectMapper.readTree(responseBody).at("/data/state").asText()
}
