package com.depromeet.piki.auth.controller

import com.depromeet.piki.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.piki.auth.infrastructure.oauth.OAuthUserInfo
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.StubOAuthClient
import com.depromeet.piki.wishlist.domain.Wish
import com.depromeet.piki.wishlist.repository.WishRepository
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@Transactional
class OAuthLoginIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    @Qualifier("kakaoOAuthClient")
    private lateinit var kakaoOAuthClient: StubOAuthClient

    @Autowired
    @Qualifier("googleOAuthClient")
    private lateinit var googleOAuthClient: StubOAuthClient

    @Autowired
    private lateinit var wishRepository: WishRepository

    private fun mockMvc(): MockMvc =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    private fun loginBody(vararg pairs: Pair<String, String>): String = objectMapper.writeValueAsString(mapOf(*pairs))

    private fun userIdOf(json: String): String = objectMapper.readTree(json).at("/data/user/id").asString()

    private data class Guest(
        val accessToken: String,
        val userId: String,
    )

    private fun createGuest(): Guest {
        val json =
            mockMvc()
                .perform(
                    post("/api/v1/auth/guest").contentType(MediaType.APPLICATION_JSON).header("X-Client-Type", "app"),
                ).andReturn()
                .response.contentAsString
        val node = objectMapper.readTree(json)
        return Guest(node.at("/data/accessToken").asString(), node.at("/data/user/id").asString())
    }

    @Test
    fun `신규 소셜 - app 으로 v2 로그인하면 MEMBER 로 가입되고 body 토큰이 온다`() {
        googleOAuthClient.fetchByAccessTokenStub =
            { OAuthUserInfo(OAuthProvider.GOOGLE, "google_fresh", "https://img/p.jpg") }

        mockMvc()
            .perform(
                post("/api/v1/auth/login/google")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Client-Type", "app")
                    .content(loginBody("accessToken" to "sdk-token")),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.user.identityType").value("MEMBER"))
            .andExpect(jsonPath("$.data.user.profileImage").value("https://img/p.jpg"))
            .andExpect(jsonPath("$.data.accessToken").isString)
            .andExpect(jsonPath("$.data.refreshToken").isString)
    }

    @Test
    fun `재방문 - 같은 소셜로 다시 로그인하면 동일 user 가 반환된다`() {
        kakaoOAuthClient.fetchByAccessTokenStub = { OAuthUserInfo(OAuthProvider.KAKAO, "kakao_return", null) }
        val body = loginBody("accessToken" to "t")

        val first =
            mockMvc()
                .perform(
                    post(
                        "/api/v1/auth/login/kakao",
                    ).contentType(MediaType.APPLICATION_JSON).header("X-Client-Type", "app").content(body),
                ).andReturn()
                .response.contentAsString
        val second =
            mockMvc()
                .perform(
                    post(
                        "/api/v1/auth/login/kakao",
                    ).contentType(MediaType.APPLICATION_JSON).header("X-Client-Type", "app").content(body),
                ).andReturn()
                .response.contentAsString

        assertEquals(userIdOf(first), userIdOf(second))
    }

    @Test
    fun `게스트 연결 - 게스트 토큰 + 신규 소셜이면 그 게스트가 MEMBER 로 승격되고 데이터를 이어받는다`() {
        val guest = createGuest()
        kakaoOAuthClient.fetchByAccessTokenStub = { OAuthUserInfo(OAuthProvider.KAKAO, "kakao_link", null) }

        mockMvc()
            .perform(
                post("/api/v1/auth/login/kakao")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Client-Type", "app")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${guest.accessToken}")
                    .content(loginBody("accessToken" to "t")),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.user.id").value(guest.userId))
            .andExpect(jsonPath("$.data.user.identityType").value("MEMBER"))
    }

    @Test
    fun `게스트 연결 - 승격 후에도 게스트가 만든 위시(데이터)가 그대로 승계된다`() {
        val guest = createGuest()
        val guestId = UUID.fromString(guest.userId)
        // 게스트 상태에서 위시 1건 생성 (user_id = 게스트 id). 승격은 id 를 유지하므로 이 행이 그대로 따라와야 한다.
        wishRepository.save(Wish(userId = guestId, itemId = 1L))
        kakaoOAuthClient.fetchByAccessTokenStub = { OAuthUserInfo(OAuthProvider.KAKAO, "kakao_inherit", null) }

        val resultId =
            userIdOf(
                mockMvc()
                    .perform(
                        post("/api/v1/auth/login/kakao")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Client-Type", "app")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer ${guest.accessToken}")
                            .content(loginBody("accessToken" to "t")),
                    ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.data.user.identityType").value("MEMBER"))
                    .andReturn()
                    .response.contentAsString,
            )

        // 승격된 멤버는 게스트와 같은 id 라, 그 id 로 만든 위시가 그대로 승계된다
        assertEquals(guest.userId, resultId)
        val wishes = wishRepository.findPage(guestId, null, 10)
        assertEquals(1, wishes.size)
        assertEquals(guestId, wishes.first().userId)
    }

    @Test
    fun `소셜 중복 - 게스트가 이미 타계정에 연결된 소셜로 로그인하면 그 기존 계정으로 로그인된다(게스트 포기)`() {
        kakaoOAuthClient.fetchByAccessTokenStub = { OAuthUserInfo(OAuthProvider.KAKAO, "kakao_dup", null) }
        val body = loginBody("accessToken" to "t")

        val userAId =
            userIdOf(
                mockMvc()
                    .perform(
                        post(
                            "/api/v1/auth/login/kakao",
                        ).contentType(MediaType.APPLICATION_JSON).header("X-Client-Type", "app").content(body),
                    ).andReturn()
                    .response.contentAsString,
            )

        val guest = createGuest()
        val resultId =
            userIdOf(
                mockMvc()
                    .perform(
                        post("/api/v1/auth/login/kakao")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Client-Type", "app")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer ${guest.accessToken}")
                            .content(body),
                    ).andReturn()
                    .response.contentAsString,
            )

        assertEquals(userAId, resultId)
        assertNotEquals(guest.userId, resultId)
    }

    @Test
    fun `v1 - code+redirectUri 로 로그인된다`() {
        googleOAuthClient.fetchByCodeStub = { _, _ -> OAuthUserInfo(OAuthProvider.GOOGLE, "google_v1", null) }

        mockMvc()
            .perform(
                post("/api/v1/auth/login/google")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Client-Type", "app")
                    .content(loginBody("code" to "auth-code", "redirectUri" to "https://app/callback")),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.accessToken").isString)
    }

    @Test
    fun `잘못된 요청 - code 도 accessToken 도 없으면 400`() {
        mockMvc()
            .perform(
                post("/api/v1/auth/login/google").contentType(MediaType.APPLICATION_JSON).content("{}"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
    }

    @Test
    fun `잘못된 요청 - accessToken 과 code 를 동시에 보내면 400`() {
        mockMvc()
            .perform(
                post("/api/v1/auth/login/google")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody("accessToken" to "t", "code" to "c", "redirectUri" to "https://app/callback")),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
    }

    @Test
    fun `미지원 provider - apple 은 400`() {
        mockMvc()
            .perform(
                post("/api/v1/auth/login/apple").contentType(MediaType.APPLICATION_JSON).content(
                    loginBody(
                        "accessToken" to "t",
                    ),
                ),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `provider 호출 실패 - 502 Bad Gateway 로 매핑된다`() {
        googleOAuthClient.fetchByAccessTokenStub = { error("google down") }

        mockMvc()
            .perform(
                post("/api/v1/auth/login/google").contentType(MediaType.APPLICATION_JSON).content(
                    loginBody(
                        "accessToken" to "t",
                    ),
                ),
            ).andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.status").value(502))
    }

    @Test
    fun `기본(헤더 없음) - 토큰을 쿠키로 내리고 body 토큰은 null 이다`() {
        googleOAuthClient.fetchByAccessTokenStub = { OAuthUserInfo(OAuthProvider.GOOGLE, "google_web", null) }

        mockMvc()
            .perform(
                post("/api/v1/auth/login/google").contentType(MediaType.APPLICATION_JSON).content(
                    loginBody(
                        "accessToken" to "t",
                    ),
                ),
            ).andExpect(status().isOk)
            .andExpect(cookie().exists("access_token"))
            .andExpect(cookie().exists("refresh_token"))
            .andExpect(jsonPath("$.data.accessToken").value(nullValue()))
    }
}
