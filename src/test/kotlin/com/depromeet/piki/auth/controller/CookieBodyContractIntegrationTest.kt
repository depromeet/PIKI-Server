package com.depromeet.piki.auth.controller

import com.depromeet.piki.auth.web.ClientType
import com.depromeet.piki.support.IntegrationTestSupport
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertTrue

// cookie + body 토큰 전달 contract (#166). WEB=쿠키전용 / APP=body전용 분리, 헤더 우선, refresh·logout.
@Transactional
class CookieBodyContractIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun mockMvc(): MockMvc =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    private fun createGuestWeb(): MvcResult =
        mockMvc()
            .perform(
                post("/api/v1/auth/guest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(ClientType.HEADER, "web"),
            ).andReturn()

    private fun createGuestApp(): MvcResult =
        mockMvc()
            .perform(post("/api/v1/auth/guest").contentType(MediaType.APPLICATION_JSON))
            .andReturn()

    @Test
    fun `WEB - guest 생성 시 토큰을 HttpOnly 쿠키로 내리고 body 토큰은 null 이다`() {
        val result =
            mockMvc()
                .perform(
                    post("/api/v1/auth/guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ClientType.HEADER, "web"),
                ).andExpect(status().isCreated)
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().httpOnly("access_token", true))
                .andExpect(cookie().path("access_token", "/"))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().path("refresh_token", "/api/v1/auth"))
                .andExpect(jsonPath("$.data.accessToken").value(nullValue()))
                .andExpect(jsonPath("$.data.refreshToken").value(nullValue()))
                .andExpect(jsonPath("$.data.user.identityType").value("GUEST"))
                .andReturn()

        // SameSite 는 jakarta Cookie 에 없어 Set-Cookie 헤더 문자열로 확인한다.
        val setCookies = result.response.getHeaders(HttpHeaders.SET_COOKIE)
        assertTrue(
            setCookies.any { it.startsWith("access_token=") && it.contains("SameSite=Strict") },
            "access_token 쿠키에 SameSite=Strict 가 있어야 한다",
        )
    }

    @Test
    fun `APP - 헤더 없으면 Set-Cookie 없이 body 로 토큰을 내린다 (회귀)`() {
        mockMvc()
            .perform(post("/api/v1/auth/guest").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated)
            .andExpect(cookie().doesNotExist("access_token"))
            .andExpect(cookie().doesNotExist("refresh_token"))
            .andExpect(jsonPath("$.data.accessToken").isString)
            .andExpect(jsonPath("$.data.refreshToken").isString)
    }

    @Test
    fun `WEB - access_token 쿠키만으로 인증 엔드포인트가 통과한다`() {
        val accessCookie = createGuestWeb().response.getCookie("access_token")!!

        mockMvc()
            .perform(get("/api/v1/users/me").cookie(accessCookie))
            .andExpect(status().isOk)
    }

    @Test
    fun `헤더가 유효하면 잘못된 쿠키가 있어도 헤더로 인증된다 (헤더 우선)`() {
        val accessToken =
            objectMapper
                .readTree(createGuestApp().response.contentAsString)
                .at("/data/accessToken")
                .asString()

        mockMvc()
            .perform(
                get("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                    .cookie(Cookie("access_token", "garbage-token")),
            ).andExpect(status().isOk)
    }

    @Test
    fun `WEB - refresh_token 쿠키로 토큰이 갱신되고 새 쿠키가 내려온다`() {
        val refreshCookie = createGuestWeb().response.getCookie("refresh_token")!!

        mockMvc()
            .perform(
                post("/api/v1/auth/token/refresh")
                    .header(ClientType.HEADER, "web")
                    .cookie(refreshCookie),
            ).andExpect(status().isOk)
            .andExpect(cookie().exists("access_token"))
            .andExpect(cookie().exists("refresh_token"))
            .andExpect(jsonPath("$.data.accessToken").value(nullValue()))
    }

    @Test
    fun `refresh - X-Client-Type 없이 refresh_token 쿠키만 있어도 쿠키로 회전한다 (하드닝)`() {
        // 웹이 refresh 에 X-Client-Type 을 빠뜨려도, 브라우저가 자동 전송한 refresh_token 쿠키로
        // 웹임을 추론해 쿠키를 회전한다. 안 그러면 옛 쿠키가 stale 로 남아 세션이 죽는다.
        val refreshCookie = createGuestWeb().response.getCookie("refresh_token")!!

        mockMvc()
            .perform(
                post("/api/v1/auth/token/refresh")
                    .cookie(refreshCookie),
            ).andExpect(status().isOk)
            .andExpect(cookie().exists("access_token"))
            .andExpect(cookie().exists("refresh_token"))
            .andExpect(jsonPath("$.data.accessToken").value(nullValue()))
    }

    @Test
    fun `refresh - X-Client-Type app 이 명시되면 쿠키가 동봉돼도 body 로 응답한다 (명시 헤더 권위)`() {
        // 명시한 app 헤더는 권위적 — 어떤 이유로 우리 쿠키가 동봉돼도 쿠키 추론으로 web 오분류되지 않는다.
        val refreshCookie = createGuestWeb().response.getCookie("refresh_token")!!

        mockMvc()
            .perform(
                post("/api/v1/auth/token/refresh")
                    .header(ClientType.HEADER, "app")
                    .cookie(refreshCookie),
            ).andExpect(status().isOk)
            .andExpect(cookie().doesNotExist("access_token"))
            .andExpect(jsonPath("$.data.accessToken").isString)
    }

    @Test
    fun `refresh - 쿠키도 body 도 없으면 400 이다`() {
        mockMvc()
            .perform(post("/api/v1/auth/token/refresh").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
    }

    @Test
    fun `logout 시 X-Client-Type 없이도 토큰 쿠키가 Max-Age 0 으로 만료된다 (무조건)`() {
        // 쿠키 만료는 fail-safe 로 ClientType 무관하게 내려가야 한다.
        // 웹이 logout 에 X-Client-Type 을 빠뜨려도 살아있는 access 쿠키가 확실히 삭제되도록.
        val accessCookie = createGuestWeb().response.getCookie("access_token")!!

        mockMvc()
            .perform(
                post("/api/v1/auth/logout")
                    .cookie(accessCookie),
            ).andExpect(status().isOk)
            .andExpect(cookie().maxAge("access_token", 0))
            .andExpect(cookie().maxAge("refresh_token", 0))
            .andExpect(jsonPath("$.data.loggedOut").value(true))
    }
}
