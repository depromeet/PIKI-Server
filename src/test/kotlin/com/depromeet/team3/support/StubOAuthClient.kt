package com.depromeet.team3.support

import com.depromeet.team3.auth.infrastructure.oauth.OAuthClient
import com.depromeet.team3.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.team3.auth.infrastructure.oauth.OAuthUserInfo

class StubOAuthClient(
    override val provider: OAuthProvider,
) : OAuthClient {
    // v1 (code/redirectUri) / v2 (accessToken) 두 흐름을 각각 stub 한다.
    // default 는 모두 throw — 명시 세팅을 빠뜨리면 호출 시점에 즉시 깨지도록 강제.
    var fetchByCodeStub: (String, String) -> OAuthUserInfo = { _, _ ->
        error("stub.fetchByCodeStub 를 테스트 본문에서 명시 세팅해야 한다.")
    }
    var fetchByAccessTokenStub: (String) -> OAuthUserInfo = { _ ->
        error("stub.fetchByAccessTokenStub 를 테스트 본문에서 명시 세팅해야 한다.")
    }

    override fun fetchUserInfoByCode(
        code: String,
        redirectUri: String,
    ): OAuthUserInfo = fetchByCodeStub(code, redirectUri)

    override fun fetchUserInfoByAccessToken(accessToken: String): OAuthUserInfo = fetchByAccessTokenStub(accessToken)
}
