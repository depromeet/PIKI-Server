package com.depromeet.team3.auth.infrastructure.oauth.kakao

import com.depromeet.team3.auth.infrastructure.oauth.OAuthClient
import com.depromeet.team3.auth.infrastructure.oauth.OAuthParams
import com.depromeet.team3.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.team3.auth.infrastructure.oauth.OAuthUserInfo
import com.depromeet.team3.auth.infrastructure.oauth.kakao.dto.KakaoTokenResponse
import com.depromeet.team3.auth.infrastructure.oauth.kakao.dto.KakaoUserInfoResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

class KakaoOAuthClient(
    private val kakaoProperties: KakaoProperties,
) : OAuthClient {
    override val provider = OAuthProvider.KAKAO

    private val authClient = RestClient.builder().baseUrl(AUTH_BASE_URL).build()
    private val apiClient = RestClient.builder().baseUrl(API_BASE_URL).build()

    // v1 — 백엔드가 code → access_token 교환 후 v2 메서드를 재사용해 user_info 조회.
    override fun fetchUserInfoByCode(
        code: String,
        redirectUri: String,
    ): OAuthUserInfo {
        val accessToken = fetchAccessToken(code, redirectUri)
        return fetchUserInfoByAccessToken(accessToken)
    }

    // v2 — 클라이언트 SDK 가 받은 access_token 으로 user_info 만 조회.
    // Kakao 의 access_token 은 v1/v2 모두 같은 종류라 추가 검증 로직 없이 그대로 재활용 가능하다.
    override fun fetchUserInfoByAccessToken(accessToken: String): OAuthUserInfo {
        val userInfo = fetchKakaoUserInfo(accessToken)
        return OAuthUserInfo(
            provider = OAuthProvider.KAKAO,
            socialId = userInfo.id.toString(),
            email = userInfo.kakaoAccount.email,
            profileImage = userInfo.kakaoAccount.profile.profileImageUrl,
        )
    }

    private fun fetchAccessToken(
        code: String,
        redirectUri: String,
    ): String {
        val params =
            LinkedMultiValueMap<String, String>().apply {
                add(OAuthParams.GRANT_TYPE, OAuthParams.GRANT_TYPE_AUTHORIZATION_CODE)
                add(OAuthParams.CLIENT_ID, kakaoProperties.clientId)
                add(OAuthParams.CLIENT_SECRET, kakaoProperties.clientSecret)
                add(OAuthParams.REDIRECT_URI, redirectUri)
                add(OAuthParams.CODE, code)
            }
        val response =
            authClient
                .post()
                .uri(TOKEN_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(params)
                .retrieve()
                .body<KakaoTokenResponse>()
                ?: error("Kakao token response body is null")
        return response.accessToken
    }

    private fun fetchKakaoUserInfo(accessToken: String): KakaoUserInfoResponse =
        apiClient
            .get()
            .uri(USER_INFO_PATH)
            .header(HttpHeaders.AUTHORIZATION, "$BEARER_PREFIX$accessToken")
            .retrieve()
            .body<KakaoUserInfoResponse>()
            ?: error("Kakao user info response body is null")

    companion object {
        private const val AUTH_BASE_URL = "https://kauth.kakao.com"
        private const val API_BASE_URL = "https://kapi.kakao.com"
        private const val TOKEN_PATH = "/oauth/token"
        private const val USER_INFO_PATH = "/v2/user/me"
        private const val BEARER_PREFIX = "Bearer "
    }
}
