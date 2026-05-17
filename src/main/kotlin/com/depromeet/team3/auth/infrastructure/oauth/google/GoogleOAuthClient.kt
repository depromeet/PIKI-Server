package com.depromeet.team3.auth.infrastructure.oauth.google

import com.depromeet.team3.auth.infrastructure.oauth.OAuthClient
import com.depromeet.team3.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.team3.auth.infrastructure.oauth.OAuthUserInfo
import com.depromeet.team3.auth.infrastructure.oauth.google.dto.GoogleTokenResponse
import com.depromeet.team3.auth.infrastructure.oauth.google.dto.GoogleUserInfoResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

class GoogleOAuthClient(
    private val googleProperties: GoogleProperties,
) : OAuthClient {
    override val provider = OAuthProvider.GOOGLE

    private val tokenClient = RestClient.builder().baseUrl(TOKEN_BASE_URL).build()
    private val userInfoClient = RestClient.builder().baseUrl(USER_INFO_BASE_URL).build()

    // v1 — 백엔드가 code → access_token 교환 후 v2 메서드를 재사용해 user_info 조회.
    override fun fetchUserInfoByCode(
        code: String,
        redirectUri: String,
    ): OAuthUserInfo {
        val accessToken = fetchAccessToken(code, redirectUri)
        return fetchUserInfoByAccessToken(accessToken)
    }

    // v2 — 클라이언트 SDK 가 받은 access_token 으로 user_info 만 조회.
    // Google SDK 는 id_token (JWT) 도 같이 반환하는데, 그 흐름은 JWKS public key 검증 로직이
    // 추가로 필요해 epic #122 (https://github.com/depromeet/PIKI-Server/issues/122) 의
    // Task 6 에서 별도 메서드로 구현한다. 본 PR 은 access_token 흐름까지만.
    override fun fetchUserInfoByAccessToken(accessToken: String): OAuthUserInfo {
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
                add(PARAM_GRANT_TYPE, GRANT_TYPE_AUTHORIZATION_CODE)
                add(PARAM_CLIENT_ID, googleProperties.clientId)
                add(PARAM_CLIENT_SECRET, googleProperties.clientSecret)
                add(PARAM_REDIRECT_URI, redirectUri)
                add(PARAM_CODE, code)
            }
        val response =
            tokenClient
                .post()
                .uri(TOKEN_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(params)
                .retrieve()
                .body<GoogleTokenResponse>()
                ?: error("Google token response body is null")
        return response.accessToken
    }

    private fun fetchGoogleUserInfo(accessToken: String): GoogleUserInfoResponse =
        userInfoClient
            .get()
            .uri(USER_INFO_PATH)
            .header(HttpHeaders.AUTHORIZATION, "$BEARER_PREFIX$accessToken")
            .retrieve()
            .body<GoogleUserInfoResponse>()
            ?: error("Google user info response body is null")

    companion object {
        private const val TOKEN_BASE_URL = "https://oauth2.googleapis.com"
        private const val USER_INFO_BASE_URL = "https://www.googleapis.com"
        private const val TOKEN_PATH = "/token"
        private const val USER_INFO_PATH = "/oauth2/v2/userinfo"
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
