package com.depromeet.team3.auth.infrastructure.oauth

import com.depromeet.team3.support.IntegrationTestSupport
import com.depromeet.team3.support.StubOAuthClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.transaction.annotation.Transactional
import kotlin.test.Test
import kotlin.test.assertEquals

@Transactional
class OAuthClientIntegrationTest : IntegrationTestSupport() {
    @Autowired
    @Qualifier("kakaoOAuthClient")
    private lateinit var kakaoOAuthClient: StubOAuthClient

    @Autowired
    @Qualifier("googleOAuthClient")
    private lateinit var googleOAuthClient: StubOAuthClient

    @Test
    fun `Kakao OAuth stub 이 설정한 OAuthUserInfo 를 반환한다`() {
        kakaoOAuthClient.fetchUserInfoStub = { _, _ ->
            OAuthUserInfo(
                provider = OAuthProvider.KAKAO,
                socialId = "kakao_123",
                email = "user@kakao.com",
                profileImage = "https://img.kakao.com/profile.jpg",
            )
        }

        val result = kakaoOAuthClient.fetchUserInfo("auth-code", "https://example.com/callback")

        assertEquals(OAuthProvider.KAKAO, result.provider)
        assertEquals("kakao_123", result.socialId)
        assertEquals("user@kakao.com", result.email)
    }

    @Test
    fun `Google OAuth stub 이 설정한 OAuthUserInfo 를 반환한다`() {
        googleOAuthClient.fetchUserInfoStub = { _, _ ->
            OAuthUserInfo(
                provider = OAuthProvider.GOOGLE,
                socialId = "google_456",
                email = "user@gmail.com",
                profileImage = "https://lh3.googleusercontent.com/profile.jpg",
            )
        }

        val result = googleOAuthClient.fetchUserInfo("auth-code", "https://example.com/callback")

        assertEquals(OAuthProvider.GOOGLE, result.provider)
        assertEquals("google_456", result.socialId)
        assertEquals("user@gmail.com", result.email)
    }

    @Test
    fun `Kakao 와 Google stub 이 각각 독립적인 provider 를 반환한다`() {
        assertEquals(OAuthProvider.KAKAO, kakaoOAuthClient.provider)
        assertEquals(OAuthProvider.GOOGLE, googleOAuthClient.provider)
    }
}
