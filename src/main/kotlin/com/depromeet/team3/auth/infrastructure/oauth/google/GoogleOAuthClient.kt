package com.depromeet.team3.auth.infrastructure.oauth.google

import com.depromeet.team3.auth.infrastructure.oauth.OAuthClient
import com.depromeet.team3.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.team3.auth.infrastructure.oauth.OAuthUserInfo
import com.depromeet.team3.auth.infrastructure.oauth.google.dto.GoogleTokenResponse
import com.depromeet.team3.auth.infrastructure.oauth.google.dto.GoogleUserInfoResponse
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

class GoogleOAuthClient(
    private val googleProperties: GoogleProperties,
) : OAuthClient {
    override val provider = OAuthProvider.GOOGLE

    private val tokenClient = RestClient.builder().baseUrl("https://oauth2.googleapis.com").build()
    private val userInfoClient = RestClient.builder().baseUrl("https://www.googleapis.com").build()

    override fun fetchUserInfo(
        code: String,
        redirectUri: String,
    ): OAuthUserInfo {
        val accessToken = fetchAccessToken(code, redirectUri)
        val userInfo = fetchGoogleUserInfo(accessToken)
        return OAuthUserInfo(
            provider = OAuthProvider.GOOGLE,
            socialId = userInfo.id,
            email = userInfo.email,
            profileImage = userInfo.picture,
        )
    }

    private fun fetchAccessToken(
        code: String,
        redirectUri: String,
    ): String {
        val params =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "authorization_code")
                add("client_id", googleProperties.clientId)
                add("client_secret", googleProperties.clientSecret)
                add("redirect_uri", redirectUri)
                add("code", code)
            }
        return tokenClient
            .post()
            .uri("/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(params)
            .retrieve()
            .body<GoogleTokenResponse>()!!
            .access_token
    }

    private fun fetchGoogleUserInfo(accessToken: String): GoogleUserInfoResponse =
        userInfoClient
            .get()
            .uri("/oauth2/v2/userinfo")
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body<GoogleUserInfoResponse>()!!
}
