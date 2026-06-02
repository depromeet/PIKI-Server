package com.depromeet.piki.auth.infrastructure.oauth.google

import com.depromeet.piki.auth.infrastructure.oauth.OAuthClient
import com.depromeet.piki.auth.infrastructure.oauth.OAuthParams
import com.depromeet.piki.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.piki.auth.infrastructure.oauth.OAuthRestClient
import com.depromeet.piki.auth.infrastructure.oauth.OAuthUserInfo
import com.depromeet.piki.auth.infrastructure.oauth.google.dto.GoogleTokenResponse
import com.depromeet.piki.auth.infrastructure.oauth.google.dto.GoogleUserInfoResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.body
import org.springframework.web.util.UriComponentsBuilder

class GoogleOAuthClient(
    private val googleProperties: GoogleProperties,
) : OAuthClient {
    override val provider = OAuthProvider.GOOGLE

    private val tokenClient = OAuthRestClient.create(TOKEN_BASE_URL)
    private val userInfoClient = OAuthRestClient.create(USER_INFO_BASE_URL)

    override fun buildAuthUrl(state: String): String =
        UriComponentsBuilder
            .fromUriString(AUTH_URL)
            .queryParam(OAuthParams.CLIENT_ID, googleProperties.clientId)
            .queryParam(OAuthParams.REDIRECT_URI, googleProperties.redirectUri)
            .queryParam(OAuthParams.RESPONSE_TYPE, OAuthParams.RESPONSE_TYPE_CODE)
            .queryParam(OAuthParams.SCOPE, "email profile")
            .queryParam(OAuthParams.STATE, state)
            .build()
            .encode()
            .toUriString()

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
            profileImage = userInfo.picture.ifBlank { null },
        )
    }

    private fun fetchAccessToken(
        code: String,
        redirectUri: String,
    ): String {
        val params =
            LinkedMultiValueMap<String, String>().apply {
                add(OAuthParams.GRANT_TYPE, OAuthParams.GRANT_TYPE_AUTHORIZATION_CODE)
                add(OAuthParams.CLIENT_ID, googleProperties.clientId)
                add(OAuthParams.CLIENT_SECRET, googleProperties.clientSecret)
                add(OAuthParams.REDIRECT_URI, redirectUri)
                add(OAuthParams.CODE, code)
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
        private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_PATH = "/token"
        private const val USER_INFO_PATH = "/oauth2/v2/userinfo"
        private const val BEARER_PREFIX = "Bearer "
    }
}
