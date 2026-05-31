package com.depromeet.piki.auth.infrastructure.oauth

import org.springframework.stereotype.Component

// provider → OAuthClient 라우팅. 등록된 모든 OAuthClient 빈을 주입받아 provider 로 색인한다.
// Apple 처럼 enum 만 있고 구현체가 없는 provider 는 맵에 없어 unsupportedProvider 로 떨어진다.
@Component
class OAuthClientRegistry(
    clients: List<OAuthClient>,
) {
    // 같은 provider 빈이 둘 이상이면 associateBy 가 조용히 마지막으로 덮어써 라우팅이 틀어질 수 있다.
    // 설정 충돌은 개발자 실수(불변식 위반)이므로 시작 시점에 require 로 fail-fast 한다.
    private val byProvider: Map<OAuthProvider, OAuthClient> =
        clients
            .groupBy { it.provider }
            .also { grouped ->
                val duplicates = grouped.filterValues { it.size > 1 }.keys
                require(duplicates.isEmpty()) { "중복 OAuthClient provider 등록: $duplicates" }
            }.mapValues { (_, list) -> list.single() }

    fun resolve(provider: OAuthProvider): OAuthClient = byProvider[provider] ?: throw OAuthException.unsupportedProvider()
}
