package com.depromeet.piki.auth.service

import com.depromeet.piki.auth.infrastructure.oauth.OAuthClient
import com.depromeet.piki.auth.infrastructure.oauth.OAuthClientRegistry
import com.depromeet.piki.auth.infrastructure.oauth.OAuthException
import com.depromeet.piki.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.piki.auth.infrastructure.oauth.OAuthUserInfo
import com.depromeet.piki.auth.infrastructure.redis.OAuthStateStore
import com.depromeet.piki.auth.service.dto.OAuthLoginCommand
import com.depromeet.piki.auth.service.dto.SignupResult
import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(javaClass)

    fun login(
        provider: OAuthProvider,
        command: OAuthLoginCommand,
        currentUserId: UUID?,
    ): SignupResult {
        // currentUserId 는 게스트가 소셜 연결을 시도하는 경우의 게스트 id(없으면 null=신규/재방문 로그인).
        // 토큰·code 등 크리덴셜은 싣지 않는다 — provider 와 게스트연결 여부만 남긴다.
        log.info("소셜 로그인 시도 provider={} currentUserId={}", provider, currentUserId)
        // state 가 있으면 Redis 에서 소비 검증. 없거나 만료된 state 는 401 — CSRF 방지.
        // state 를 보내지 않으면 검증 생략 (v2 SDK 흐름 · 과도기 호환).
        command.state?.ifBlank { null }?.let { state ->
            if (!oAuthStateStore.consumeIfValid(state)) {
                // 클라이언트 계약 위반(만료·위조 state)이라 info — 서버 입장에선 정상 거부다.
                log.info("소셜 로그인 거부 사유=state 검증 실패(CSRF 방지) provider={}", provider)
                throw OAuthException.invalidState()
            }
        }
        val client = oAuthClientRegistry.resolve(provider)
        val userInfo = fetchUserInfo(client, command) // 외부 호출, tx 밖
        val user = socialAccountService.resolveUser(userInfo, currentUserId) // 영속화, 짧은 tx
        val tokenPair = authService.createTokensForUser(user)
        log.info("소셜 로그인 성공 provider={} userId={} identityType={}", provider, user.id, user.identityType)
        return SignupResult(tokenPair = tokenPair, user = user)
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
        val code =
            command.code?.ifBlank { null }
                ?: run {
                    log.info("OAuth 요청 흐름 누락: code 없음")
                    throw OAuthException.invalidRequest()
                }
        val redirectUri =
            command.redirectUri?.ifBlank { null }
                ?: run {
                    log.info("OAuth 요청 흐름 누락: redirectUri 없음")
                    throw OAuthException.invalidRequest()
                }
        return runProvider { client.fetchUserInfoByCode(code, redirectUri) }
    }

    // provider 호출 실패(네트워크·4xx/5xx·역직렬화 등)는 외부 의존성 장애로 502 로 매핑한다.
    private inline fun runProvider(block: () -> OAuthUserInfo): OAuthUserInfo =
        try {
            block()
        } catch (e: OAuthException) {
            throw e
        } catch (e: Exception) {
            // provider 장애는 GlobalExceptionHandler 가 OAuthException(cause=e)을 category 기준 레벨로 남긴다
            // (RETRYABLE=warn / SERVER_ERROR=error, 스택에 cause 포함). 여기서 raw e 를 또 찍으면 중복이고
            // 원문 노출 표면만 늘어, 로깅은 핸들러에 일임하고 여기선 래핑만 한다.
            throw OAuthException.providerError(e)
        }
}
