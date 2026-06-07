package com.depromeet.piki.auth.infrastructure.oauth.google.dto

import com.depromeet.piki.auth.infrastructure.oauth.OAuthProvider
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GoogleUserInfoResponseTest {
    @Test
    fun `email 이 있으면 OAuthUserInfo 로 매핑된다`() {
        val response = GoogleUserInfoResponse(id = "google-123", picture = "https://img/p.png", email = "user@gmail.com")

        val result = response.toOAuthUserInfo()

        assertEquals(OAuthProvider.GOOGLE, result.provider)
        assertEquals("google-123", result.socialId)
        assertEquals("https://img/p.png", result.profileImage)
        assertEquals("user@gmail.com", result.email)
    }

    @Test
    fun `email 이 null 이면(미동의) email 은 null 이다`() {
        val response = GoogleUserInfoResponse(id = "google-123", email = null)

        assertEquals(null, response.toOAuthUserInfo().email)
    }

    @Test
    fun `email 이 빈 문자열이면 null 로 정규화된다`() {
        val response = GoogleUserInfoResponse(id = "google-123", email = "")

        assertEquals(null, response.toOAuthUserInfo().email)
    }

    @Test
    fun `picture 가 빈 문자열이면 profileImage 는 null 이다`() {
        val response = GoogleUserInfoResponse(id = "google-123", picture = "")

        assertEquals(null, response.toOAuthUserInfo().profileImage)
    }
}
