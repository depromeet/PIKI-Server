package com.depromeet.piki.user.controller

import com.depromeet.piki.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.piki.auth.infrastructure.oauth.OAuthUserInfo
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.StubOAuthClient
import com.depromeet.piki.user.service.DefaultProfileImages
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertTrue

// application-test 의 s3.public-base-url 기준으로 단언한다.
@Transactional
class DefaultProfileImageIntegrationTest : IntegrationTestSupport() {
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

    // 테스트 yml 의 s3.public-base-url 을 직접 주입받아 하드코딩 동기화 부담을 없앤다(yml 값이 바뀌어도 자동 일치).
    @Value("\${s3.public-base-url}")
    private lateinit var publicBaseUrl: String

    // @Value 는 생성 후 주입되므로 lazy 로 접근 시점에 조립한다(인라인 초기화는 lateinit 미초기화로 깨진다).
    private val defaultAvatarUrls: Set<String> by lazy {
        (1..DefaultProfileImages.COUNT).map { "$publicBaseUrl/defaults/user-profile-$it.png" }.toSet()
    }

    private fun mockMvc(): MockMvc =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    private fun loginBody(vararg pairs: Pair<String, String>): String = objectMapper.writeValueAsString(mapOf(*pairs))

    private fun profileImageOf(json: String): String = objectMapper.readTree(json).at("/data/user/profileImage").asString()

    private data class Guest(
        val accessToken: String,
        val profileImage: String,
    )

    private fun createGuest(): Guest {
        val json =
            mockMvc()
                .perform(
                    post("/api/v1/auth/guest").contentType(MediaType.APPLICATION_JSON).header("X-Client-Type", "app"),
                ).andReturn()
                .response.contentAsString
        val node = objectMapper.readTree(json)
        return Guest(node.at("/data/accessToken").asString(), node.at("/data/user/profileImage").asString())
    }

    @Test
    fun `게스트 가입 - profileImage 가 기본 아바타 4종 중 하나다 (dicebear 아님)`() {
        val json =
            mockMvc()
                .perform(
                    post("/api/v1/auth/guest").contentType(MediaType.APPLICATION_JSON).header("X-Client-Type", "app"),
                ).andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString

        val profileImage = profileImageOf(json)
        assertTrue(profileImage in defaultAvatarUrls, "기본 아바타 4종이 아니다: $profileImage")
        assertTrue(!profileImage.contains("dicebear"), "dicebear URL 이 남아 있다: $profileImage")
    }

    @Test
    fun `OAuth 신규 소셜 가입 - provider 프사가 있으면 그 값을 그대로 쓴다`() {
        googleOAuthClient.fetchByAccessTokenStub =
            { OAuthUserInfo(OAuthProvider.GOOGLE, "google_with_image", "https://provider/p.jpg") }

        mockMvc()
            .perform(
                post("/api/v1/auth/login/google")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Client-Type", "app")
                    .content(loginBody("accessToken" to "t")),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.user.identityType").value("MEMBER"))
            .andExpect(jsonPath("$.data.user.profileImage").value("https://provider/p.jpg"))
    }

    @Test
    fun `OAuth 신규 소셜 가입 - provider 프사가 없으면 기본 아바타 4종 중 하나가 지정된다`() {
        kakaoOAuthClient.fetchByAccessTokenStub = { OAuthUserInfo(OAuthProvider.KAKAO, "kakao_no_image", null) }

        val json =
            mockMvc()
                .perform(
                    post("/api/v1/auth/login/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Client-Type", "app")
                        .content(loginBody("accessToken" to "t")),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.user.identityType").value("MEMBER"))
                .andReturn()
                .response.contentAsString

        val profileImage = profileImageOf(json)
        assertTrue(profileImage in defaultAvatarUrls, "기본 아바타 4종이 아니다: $profileImage")
    }

    @Test
    fun `게스트 승격 - provider 프사가 있으면 게스트의 기본 아바타가 provider 프사로 전환된다`() {
        val guest = createGuest()
        // 게스트는 처음에 기본 아바타를 갖는다.
        assertTrue(guest.profileImage in defaultAvatarUrls, "게스트 초기 프사가 기본 아바타가 아니다: ${guest.profileImage}")
        kakaoOAuthClient.fetchByAccessTokenStub =
            { OAuthUserInfo(OAuthProvider.KAKAO, "kakao_promote_image", "https://provider/switched.jpg") }

        mockMvc()
            .perform(
                post("/api/v1/auth/login/kakao")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Client-Type", "app")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${guest.accessToken}")
                    .content(loginBody("accessToken" to "t")),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.user.identityType").value("MEMBER"))
            .andExpect(jsonPath("$.data.user.profileImage").value("https://provider/switched.jpg"))
    }

    @Test
    fun `게스트 승격 - provider 프사가 없으면 게스트의 기본 아바타가 그대로 유지된다`() {
        val guest = createGuest()
        kakaoOAuthClient.fetchByAccessTokenStub = { OAuthUserInfo(OAuthProvider.KAKAO, "kakao_promote_noimage", null) }

        mockMvc()
            .perform(
                post("/api/v1/auth/login/kakao")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Client-Type", "app")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${guest.accessToken}")
                    .content(loginBody("accessToken" to "t")),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.user.identityType").value("MEMBER"))
            .andExpect(jsonPath("$.data.user.profileImage").value(guest.profileImage))
    }
}
