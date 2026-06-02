package com.depromeet.piki.auth.service

import com.depromeet.piki.auth.infrastructure.oauth.OAuthClient
import com.depromeet.piki.auth.infrastructure.oauth.OAuthClientRegistry
import com.depromeet.piki.auth.infrastructure.oauth.OAuthException
import com.depromeet.piki.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.piki.auth.infrastructure.oauth.OAuthUserInfo
import com.depromeet.piki.auth.infrastructure.redis.OAuthStateStore
import com.depromeet.piki.auth.service.dto.OAuthLoginCommand
import com.depromeet.piki.auth.service.dto.SignupResult
import org.springframework.stereotype.Service
import java.util.UUID

// 소셜 로그인 오케스트레이션. 외부 OAuth 호출(provider user_info 조회)은 트랜잭션 밖에서 끝내고,
// 영속화(find-or-create/link)는 SocialAccountService 의 짧은 트랜잭션에 위임한다.
@Service
class OAuthLoginService(
    private val oAuthClientRegistry: OAuthClientRegistry,
    private val socialAccountService: SocialAccountService,
    private val authService: AuthService,
    private val oAuthStateStore: OAuthStateStore,
) {
    fun login(
        provider: OAuthProvider,
        command: OAuthLoginCommand,
        currentUserId: UUID?,
    ): SignupResult {
        // state 가 있으면 Redis 에서 소비 검증. 없거나 만료된 state 는 401 — CSRF 방지.
        // state 를 보내지 않으면 검증 생략 (v2 SDK 흐름 · 과도기 호환).
        command.state?.let { state ->
            if (!oAuthStateStore.consumeIfValid(state)) throw OAuthException.invalidState()
        }
        val client = oAuthClientRegistry.resolve(provider)
        val userInfo = fetchUserInfo(client, command) // 외부 호출, tx 밖
        val user = socialAccountService.resolveUser(userInfo, currentUserId) // 영속화, 짧은 tx
        return authService.issueTokensFor(user)
    }

    // accessToken(v2) 이 있으면 SDK 흐름, 아니면 code+redirectUri(v1) 흐름. 둘 다 없으면 400.
    private fun fetchUserInfo(
        client: OAuthClient,
        command: OAuthLoginCommand,
    ): OAuthUserInfo {
        // v1 XOR v2 계약은 입력 경계(OAuthLoginRequest.validFlow)가 강제한다. 여기선 흐름 선택 + null-safety 만.
        command.accessToken?.ifBlank { null }?.let { accessToken ->
            return runProvider { client.fetchUserInfoByAccessToken(accessToken) }
        }
        val code = command.code?.ifBlank { null } ?: throw OAuthException.invalidRequest()
        val redirectUri = command.redirectUri?.ifBlank { null } ?: throw OAuthException.invalidRequest()
        return runProvider { client.fetchUserInfoByCode(code, redirectUri) }
    }

    // provider 호출 실패(네트워크·4xx/5xx·역직렬화 등)는 외부 의존성 장애로 502 로 매핑한다.
    private inline fun runProvider(block: () -> OAuthUserInfo): OAuthUserInfo =
        try {
            block()
        } catch (e: OAuthException) {
            throw e
        } catch (e: Exception) {
            throw OAuthException.providerError(e)
        }
}
