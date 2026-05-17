package com.depromeet.team3.auth.infrastructure.oauth.kakao

import com.depromeet.team3.auth.infrastructure.oauth.OAuthClient
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
                add(PARAM_GRANT_TYPE, GRANT_TYPE_AUTHORIZATION_CODE)
                add(PARAM_CLIENT_ID, kakaoProperties.clientId)
                add(PARAM_CLIENT_SECRET, kakaoProperties.clientSecret)
                add(PARAM_REDIRECT_URI, redirectUri)
                add(PARAM_CODE, code)
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

        // OAuth 2.0 (RFC 6749) 표준 form 파라미터. Spring Security 의 OAuth2ParameterNames 와 같은 값이지만
        // spring-security-oauth2-core 의존성 도입을 피하기 위해 자체 상수로 보관한다.
        private const val PARAM_GRANT_TYPE = "grant_type"
        private const val PARAM_CLIENT_ID = "client_id"
        private const val PARAM_CLIENT_SECRET = "client_secret"
        private const val PARAM_REDIRECT_URI = "redirect_uri"
        private const val PARAM_CODE = "code"
        private const val GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code"
    }
}
