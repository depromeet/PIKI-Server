package com.depromeet.piki.support

import com.depromeet.piki.auth.infrastructure.oauth.OAuthClient
import com.depromeet.piki.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.piki.auth.infrastructure.oauth.OAuthUserInfo

class StubOAuthClient(
    override val provider: OAuthProvider,
) : OAuthClient {
    // v1 (code/redirectUri) / v2 (accessToken) / URL 생성 흐름을 각각 stub 한다.
    // default 는 모두 throw — 명시 세팅을 빠뜨리면 호출 시점에 즉시 깨지도록 강제.
    var fetchByCodeStub: (String, String) -> OAuthUserInfo = { _, _ ->
        error("stub.fetchByCodeStub 를 테스트 본문에서 명시 세팅해야 한다.")
    }
    var fetchByAccessTokenStub: (String) -> OAuthUserInfo = { _ ->
        error("stub.fetchByAccessTokenStub 를 테스트 본문에서 명시 세팅해야 한다.")
    }
    var buildAuthUrlStub: (String, String?) -> String = { state, redirectUri ->
        "https://stub-auth.example.com/oauth/authorize?provider=${provider.name.lowercase()}&state=$state" +
            (redirectUri?.let { "&redirect_uri=$it" } ?: "")
    }
    // OAuthUrlService 가 redirectUri 검증에 사용한다. 테스트 본문에서 허용할 URI 를 명시 세팅한다.
    var allowedRedirectUrisStub: List<String> = emptyList()
    override val allowedRedirectUris: List<String> get() = allowedRedirectUrisStub

    override fun buildAuthUrl(state: String, redirectUri: String?): String = buildAuthUrlStub(state, redirectUri)

    override fun fetchUserInfoByCode(
        code: String,
        redirectUri: String,
    ): OAuthUserInfo = fetchByCodeStub(code, redirectUri)

    override fun fetchUserInfoByAccessToken(accessToken: String): OAuthUserInfo = fetchByAccessTokenStub(accessToken)
}
