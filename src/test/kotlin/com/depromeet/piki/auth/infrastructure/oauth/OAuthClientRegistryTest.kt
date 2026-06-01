package com.depromeet.piki.auth.infrastructure.oauth

import com.depromeet.piki.support.StubOAuthClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OAuthClientRegistryTest {
    @Test
    fun `등록된 provider 는 해당 client 로 resolve 된다`() {
        val registry =
            OAuthClientRegistry(
                listOf(StubOAuthClient(OAuthProvider.KAKAO), StubOAuthClient(OAuthProvider.GOOGLE)),
            )

        assertEquals(OAuthProvider.KAKAO, registry.resolve(OAuthProvider.KAKAO).provider)
        assertEquals(OAuthProvider.GOOGLE, registry.resolve(OAuthProvider.GOOGLE).provider)
    }

    @Test
    fun `구현체가 없는 provider(apple)는 unsupportedProvider 로 거절된다`() {
        val registry = OAuthClientRegistry(listOf(StubOAuthClient(OAuthProvider.KAKAO)))

        assertFailsWith<OAuthException> { registry.resolve(OAuthProvider.APPLE) }
    }

    @Test
    fun `동일 provider 를 두 번 등록하면 require 로 fail-fast 한다`() {
        assertFailsWith<IllegalArgumentException> {
            OAuthClientRegistry(
                listOf(StubOAuthClient(OAuthProvider.KAKAO), StubOAuthClient(OAuthProvider.KAKAO)),
            )
        }
    }
}
