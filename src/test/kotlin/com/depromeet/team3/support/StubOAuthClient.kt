package com.depromeet.team3.support

import com.depromeet.team3.auth.infrastructure.oauth.OAuthClient
import com.depromeet.team3.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.team3.auth.infrastructure.oauth.OAuthUserInfo

class StubOAuthClient(
    override val provider: OAuthProvider,
) : OAuthClient {
    var fetchUserInfoStub: (String, String) -> OAuthUserInfo = { _, _ ->
        error("stub.fetchUserInfoStub 를 테스트 본문에서 명시 세팅해야 한다.")
    }

    override fun fetchUserInfo(
        code: String,
        redirectUri: String,
    ): OAuthUserInfo = fetchUserInfoStub(code, redirectUri)
}
