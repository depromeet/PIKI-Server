package com.depromeet.piki.auth.service

import com.depromeet.piki.auth.infrastructure.oauth.OAuthClient
import com.depromeet.piki.auth.infrastructure.oauth.OAuthClientRegistry
import com.depromeet.piki.auth.infrastructure.oauth.OAuthException
import com.depromeet.piki.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.piki.auth.infrastructure.oauth.OAuthUserInfo
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
) {
    fun login(
        provider: OAuthProvider,
        command: OAuthLoginCommand,
        currentUserId: UUID?,
    ): SignupResult {
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
        val accessToken = command.accessToken?.ifBlank { null }
        val code = command.code?.ifBlank { null }
        val redirectUri = command.redirectUri?.ifBlank { null }

        // v2(accessToken) 흐름. code/redirectUri 가 함께 오면 어느 흐름인지 모호하므로 400 (silent v2 채택 금지).
        accessToken?.let { token ->
            (code ?: redirectUri)?.let { throw OAuthException.invalidRequest() }
            return runProvider { client.fetchUserInfoByAccessToken(token) }
        }
        // v1(code+redirectUri) 흐름. 둘 중 하나라도 없으면 400.
        val resolvedCode = code ?: throw OAuthException.invalidRequest()
        val resolvedRedirectUri = redirectUri ?: throw OAuthException.invalidRequest()
        return runProvider { client.fetchUserInfoByCode(resolvedCode, resolvedRedirectUri) }
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
