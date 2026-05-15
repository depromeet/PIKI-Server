package com.depromeet.team3.auth.infrastructure.oauth.kakao

import com.depromeet.team3.auth.infrastructure.oauth.OAuthClient
import com.depromeet.team3.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.team3.auth.infrastructure.oauth.OAuthUserInfo
import com.depromeet.team3.auth.infrastructure.oauth.kakao.dto.KakaoTokenResponse
import com.depromeet.team3.auth.infrastructure.oauth.kakao.dto.KakaoUserInfoResponse
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

class KakaoOAuthClient(
    private val kakaoProperties: KakaoProperties,
) : OAuthClient {
    override val provider = OAuthProvider.KAKAO

    private val authClient = RestClient.builder().baseUrl("https://kauth.kakao.com").build()
    private val apiClient = RestClient.builder().baseUrl("https://kapi.kakao.com").build()

    override fun fetchUserInfo(
        code: String,
        redirectUri: String,
    ): OAuthUserInfo {
        val accessToken = fetchAccessToken(code, redirectUri)
        val userInfo = fetchKakaoUserInfo(accessToken)
        return OAuthUserInfo(
            provider = OAuthProvider.KAKAO,
            socialId = userInfo.id.toString(),
            email = userInfo.kakao_account.email,
            profileImage = userInfo.kakao_account.profile.profile_image_url,
        )
    }

    private fun fetchAccessToken(
        code: String,
        redirectUri: String,
    ): String {
        val params =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "authorization_code")
                add("client_id", kakaoProperties.clientId)
                add("client_secret", kakaoProperties.clientSecret)
                add("redirect_uri", redirectUri)
                add("code", code)
            }
        return authClient
            .post()
            .uri("/oauth/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(params)
            .retrieve()
            .body<KakaoTokenResponse>()!!
            .access_token
    }

    private fun fetchKakaoUserInfo(accessToken: String): KakaoUserInfoResponse =
        apiClient
            .get()
            .uri("/v2/user/me")
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body<KakaoUserInfoResponse>()!!
}
