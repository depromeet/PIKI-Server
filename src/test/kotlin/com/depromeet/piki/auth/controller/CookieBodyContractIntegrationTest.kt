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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// cookie + body 토큰 전달 contract (#166). secure by default — 기본(미설정·web)=쿠키전용,
// X-Client-Type: app 명시일 때만 body 전용. + 쿠키 인증·refresh 회전·logout 만료.
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

    // 기본(헤더 없음)이 곧 WEB 이라 헤더 없이 호출해 쿠키를 받는다.
    private fun createGuestWeb(): MvcResult =
        mockMvc()
            .perform(post("/api/v1/auth/guest").contentType(MediaType.APPLICATION_JSON))
            .andReturn()

    // body 토큰을 받으려면 app 을 명시해야 한다.
    private fun createGuestApp(): MvcResult =
        mockMvc()
            .perform(
                post("/api/v1/auth/guest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(ClientType.HEADER, "app"),
            ).andReturn()

    @Test
    fun `기본(헤더 없음) - 토큰을 HttpOnly 쿠키로 내리고 body 토큰은 null 이다 (secure by default)`() {
        val result =
            mockMvc()
                .perform(post("/api/v1/auth/guest").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated)
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().httpOnly("access_token", true))
                .andExpect(cookie().path("access_token", "/"))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(jsonPath("$.data.accessToken").value(nullValue()))
                .andExpect(jsonPath("$.data.refreshToken").value(nullValue()))
                .andExpect(jsonPath("$.data.user.identityType").value("GUEST"))
                .andReturn()

        // SameSite·정확한 Path 는 jakarta Cookie / cookie() 매처로 단언하기 모호하므로(refresh_token 이
        // 새 쿠키 + 전환기 cleanup 쿠키로 2개 내려간다) Set-Cookie 헤더 문자열로 확인한다.
        val setCookies = result.response.getHeaders(HttpHeaders.SET_COOKIE)
        assertTrue(
            setCookies.any { it.startsWith("access_token=") && it.contains("SameSite=Strict") },
            "access_token 쿠키에 SameSite=Strict 가 있어야 한다",
        )
        // #512: refresh_token 은 Path=/ 로 넓혀 내려간다 — 엣지 proxy 가 페이지 네비게이션에서 읽도록.
        assertTrue(
            setCookies.any { it.startsWith("refresh_token=") && !it.startsWith("refresh_token=;") && it.contains("Path=/;") },
            "값이 있는 refresh_token 은 Path=/ 로 내려가야 한다",
        )
        // 전환기 cleanup: 옛 Path=/api/v1/auth refresh_token 쿠키를 빈 값·Max-Age=0 으로 함께 만료시킨다.
        assertTrue(
            setCookies.any { it.startsWith("refresh_token=;") && it.contains("Path=/api/v1/auth") && it.contains("Max-Age=0") },
            "옛 Path=/api/v1/auth refresh_token 쿠키가 Max-Age=0 으로 만료돼야 한다",
        )
    }

    @Test
    fun `APP - X-Client-Type app 명시 시 Set-Cookie 없이 body 로 토큰을 내린다`() {
        mockMvc()
            .perform(
                post("/api/v1/auth/guest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(ClientType.HEADER, "app"),
            ).andExpect(status().isCreated)
            .andExpect(cookie().doesNotExist("access_token"))
            .andExpect(cookie().doesNotExist("refresh_token"))
            .andExpect(jsonPath("$.data.accessToken").isString)
            .andExpect(jsonPath("$.data.refreshToken").isString)
    }

    @Test
    fun `기본 - access_token 쿠키만으로 인증 엔드포인트가 통과한다`() {
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
    fun `기본 - refresh_token 쿠키로 토큰이 갱신되고 새 쿠키 값이 회전된다`() {
        val oldRefresh = createGuestWeb().response.getCookie("refresh_token")!!

        val result =
            mockMvc()
                .perform(
                    post("/api/v1/auth/token/refresh").cookie(oldRefresh),
                ).andExpect(status().isOk)
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().exists("refresh_token"))
                // WEB 계약: 두 body 토큰 모두 null(XSS 탈취 창 축소). guest WEB 테스트와 동일 강도.
                .andExpect(jsonPath("$.data.accessToken").value(nullValue()))
                .andExpect(jsonPath("$.data.refreshToken").value(nullValue()))
                .andReturn()

        // 존재만이 아니라 값이 실제로 바뀌었는지까지 본다 — 같은 토큰을 재발급하는 회귀를 잡기 위함.
        val newRefresh = result.response.getCookie("refresh_token")!!.value
        assertNotEquals(oldRefresh.value, newRefresh, "refresh_token 이 회전돼 값이 바뀌어야 한다")
    }

    @Test
    fun `APP - X-Client-Type app 이 명시되면 쿠키가 동봉돼도 body 로 응답한다 (명시 헤더 권위)`() {
        // app 을 명시하면 쿠키가 동봉돼도 body 로 준다 — 브라우저 외 클라가 쿠키를 갖고 있어도 app 계약을 존중.
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
    }

    @Test
    fun `logout 시 X-Client-Type 없이도 토큰 쿠키가 Max-Age 0 으로 만료된다 (무조건)`() {
        // 쿠키 만료는 fail-safe 로 ClientType 무관하게 내려가야 한다. 살아있는 access 쿠키가 확실히 삭제되도록.
        val accessCookie = createGuestWeb().response.getCookie("access_token")!!

        val result =
            mockMvc()
                .perform(
                    post("/api/v1/auth/logout").cookie(accessCookie),
                ).andExpect(status().isOk)
                .andExpect(cookie().maxAge("access_token", 0))
                // path 까지 set 시점과 동일해야 브라우저가 실제로 삭제한다. path 가 틀려도 Max-Age=0 만 보면 통과하므로 함께 고정.
                .andExpect(cookie().path("access_token", "/"))
                .andExpect(jsonPath("$.data.loggedOut").value(true))
                .andReturn()

        // #512: refresh_token 만료는 새 Path=/ 와 옛 Path=/api/v1/auth 를 둘 다 내려야 브라우저가 실제로 지운다.
        // (옛 경로 쿠키를 안 지우면 만료까지 잔존해 갱신 경로에서 회전 토큰 불일치를 일으킨다.)
        val setCookies = result.response.getHeaders(HttpHeaders.SET_COOKIE)
        assertTrue(
            setCookies.any { it.startsWith("refresh_token=;") && it.contains("Path=/;") && it.contains("Max-Age=0") },
            "새 Path=/ refresh_token 이 Max-Age=0 으로 만료돼야 한다",
        )
        assertTrue(
            setCookies.any { it.startsWith("refresh_token=;") && it.contains("Path=/api/v1/auth") && it.contains("Max-Age=0") },
            "옛 Path=/api/v1/auth refresh_token 이 Max-Age=0 으로 만료돼야 한다",
        )
    }
}
