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
    fun `Kakao v1 fetchUserInfoByCode stub 이 설정한 OAuthUserInfo 를 반환한다`() {
        kakaoOAuthClient.fetchByCodeStub = { _, _ ->
            OAuthUserInfo(
                provider = OAuthProvider.KAKAO,
                socialId = "kakao_123",
                email = "user@kakao.com",
                profileImage = "https://img.kakao.com/profile.jpg",
            )
        }

        val result = kakaoOAuthClient.fetchUserInfoByCode("auth-code", "https://example.com/callback")

        assertEquals(OAuthProvider.KAKAO, result.provider)
        assertEquals("kakao_123", result.socialId)
        assertEquals("user@kakao.com", result.email)
    }

    @Test
    fun `Kakao v2 fetchUserInfoByAccessToken stub 이 설정한 OAuthUserInfo 를 반환한다`() {
        kakaoOAuthClient.fetchByAccessTokenStub = { _ ->
            OAuthUserInfo(
                provider = OAuthProvider.KAKAO,
                socialId = "kakao_sdk_123",
                email = "user@kakao.com",
                profileImage = "https://img.kakao.com/profile.jpg",
            )
        }

        val result = kakaoOAuthClient.fetchUserInfoByAccessToken("sdk-access-token")

        assertEquals(OAuthProvider.KAKAO, result.provider)
        assertEquals("kakao_sdk_123", result.socialId)
    }

    @Test
    fun `Google v1 fetchUserInfoByCode stub 이 설정한 OAuthUserInfo 를 반환한다`() {
        googleOAuthClient.fetchByCodeStub = { _, _ ->
            OAuthUserInfo(
                provider = OAuthProvider.GOOGLE,
                socialId = "google_456",
                email = "user@gmail.com",
                profileImage = "https://lh3.googleusercontent.com/profile.jpg",
            )
        }

        val result = googleOAuthClient.fetchUserInfoByCode("auth-code", "https://example.com/callback")

        assertEquals(OAuthProvider.GOOGLE, result.provider)
        assertEquals("google_456", result.socialId)
        assertEquals("user@gmail.com", result.email)
    }

    @Test
    fun `Google v2 fetchUserInfoByAccessToken stub 이 설정한 OAuthUserInfo 를 반환한다`() {
        googleOAuthClient.fetchByAccessTokenStub = { _ ->
            OAuthUserInfo(
                provider = OAuthProvider.GOOGLE,
                socialId = "google_sdk_456",
                email = "user@gmail.com",
                profileImage = "https://lh3.googleusercontent.com/profile.jpg",
            )
        }

        val result = googleOAuthClient.fetchUserInfoByAccessToken("sdk-access-token")

        assertEquals(OAuthProvider.GOOGLE, result.provider)
        assertEquals("google_sdk_456", result.socialId)
    }

    @Test
    fun `Kakao 와 Google stub 이 각각 독립적인 provider 를 반환한다`() {
        assertEquals(OAuthProvider.KAKAO, kakaoOAuthClient.provider)
        assertEquals(OAuthProvider.GOOGLE, googleOAuthClient.provider)
    }
}
