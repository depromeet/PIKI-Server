package com.depromeet.piki.auth.infrastructure.oauth

import org.springframework.stereotype.Component

// provider → OAuthClient 라우팅. 등록된 모든 OAuthClient 빈을 주입받아 provider 로 색인한다.
// Apple 처럼 enum 만 있고 구현체가 없는 provider 는 맵에 없어 unsupportedProvider 로 떨어진다.
@Component
class OAuthClientRegistry(
    clients: List<OAuthClient>,
) {
    private val byProvider: Map<OAuthProvider, OAuthClient> = clients.associateBy { it.provider }

    fun resolve(provider: OAuthProvider): OAuthClient = byProvider[provider] ?: throw OAuthException.unsupportedProvider()
}
