package com.depromeet.piki.auth.service

import com.depromeet.piki.auth.infrastructure.oauth.OAuthClientRegistry
import com.depromeet.piki.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.piki.auth.infrastructure.redis.OAuthStateStore
import com.depromeet.piki.auth.service.dto.OAuthUrlResult
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class OAuthUrlService(
    private val oAuthClientRegistry: OAuthClientRegistry,
    private val oAuthStateStore: OAuthStateStore,
) {
    fun buildUrl(provider: OAuthProvider): OAuthUrlResult {
        val state = UUID.randomUUID().toString()
        val client = oAuthClientRegistry.resolve(provider)
        val url = client.buildAuthUrl(state)
        oAuthStateStore.store(state)
        return OAuthUrlResult(url = url, state = state)
    }
}
