package com.depromeet.piki.auth.infrastructure.oauth.google

import com.depromeet.piki.auth.infrastructure.oauth.OAuthException
import org.springframework.http.HttpStatus
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

/**
 * Google OAuth provider 의 에러 응답을 [OAuthException] 으로 분류하는 순수 함수.
 *
 * PLAN.md Google 매핑표 + '견고함의 3원칙' 을 따른다:
 *  1. 메시지 문자열(`error_description`)에 의존하지 않는다 — 안정 필드(`error` 코드 / HTTP status)로만 분기.
 *  2. provider HTTP status 를 그대로 전달하지 않고 시맨틱 기반으로 재매핑한다.
 *  3. 미지의 코드·파싱 실패는 502 [OAuthException.providerError] 로 안전하게 fallback.
 *
 * 분류 우선순위: HTTP status 1차 → 안정 바디 필드(`error`) 2차 → 502 fallback 3차.
 *
 * Spring · RestClient 에 의존하지 않아 단위 테스트로 분기를 망라한다
 * (CLAUDE.md 테스트 결정트리 (6) 외부 호출 결과 분기).
 */
object GoogleOAuthErrorClassifier {
    // 분류는 순수 함수라 Spring 주입 ObjectMapper 를 받지 않고 자체 mapper 를 둔다 (불변·스레드세이프).
    private val jsonMapper: JsonMapper = JsonMapper.builder().build()

    // token endpoint (RFC 6749 §5.2) — 우리 설정 오류를 가리키는 안정 `error` 코드들 → 500.
    private val MISCONFIG_TOKEN_ERRORS =
        setOf("invalid_client", "unauthorized_client", "unsupported_grant_type", "invalid_scope")

    // token endpoint — 클라(또는 보수 매핑)로 도달 가능한 `error` 코드들 → 400.
    private val CLIENT_TOKEN_ERRORS = setOf("invalid_grant", "invalid_request")

    // RFC 6749 token 에러의 top-level 안정 필드명.
    private const val FIELD_ERROR = "error"

    // userinfo 에서 access_token 무효/만료를 가리키는 HTTP status (매직넘버 대신 의미로 분기).
    private val USER_INFO_UNAUTHORIZED_STATUS = HttpStatus.UNAUTHORIZED.value()

    /**
     * @param endpoint 호출 종류 (token 교환 vs userinfo). 같은 status·바디라도 해석이 다르다.
     * @param statusCode provider 가 내려준 HTTP status (4xx/5xx).
     * @param body provider 응답 바디 원문. 파싱 실패해도 안전하게 fallback.
     * @param cause 원본 예외(`RestClientResponseException` 등). message 노출 없이 cause 로만 보존.
     */
    fun classify(
        endpoint: GoogleOAuthEndpoint,
        statusCode: Int,
        body: String,
        cause: Throwable? = null,
    ): OAuthException =
        when (endpoint) {
            GoogleOAuthEndpoint.TOKEN -> classifyToken(body, cause)
            GoogleOAuthEndpoint.USER_INFO -> classifyUserInfo(statusCode, cause)
        }

    // token endpoint: HTTP status 가 400/401 로 혼재 가능 → 안정 `error` 코드로 판별 (PLAN.md 표).
    private fun classifyToken(
        body: String,
        cause: Throwable?,
    ): OAuthException {
        val error = readErrorCode(body) ?: return OAuthException.providerError(toCause(cause))
        return when (error) {
            in CLIENT_TOKEN_ERRORS -> OAuthException.invalidGrant()
            in MISCONFIG_TOKEN_ERRORS -> OAuthException.misconfigured(toCause(cause))
            else -> OAuthException.providerError(toCause(cause)) // 미지의 error 코드 → 502 fallback
        }
    }

    // userinfo: access_token 무효/만료는 HTTP 401 로 온다 — status 401 을 1차 신호로 → 401.
    // 그 외(403/5xx/연결실패 등)는 502 fallback 으로 둔다 (403 scope 부족 케이스 판단은 별도 후속).
    private fun classifyUserInfo(
        statusCode: Int,
        cause: Throwable?,
    ): OAuthException =
        when (statusCode) {
            USER_INFO_UNAUTHORIZED_STATUS -> OAuthException.invalidProviderToken()
            else -> OAuthException.providerError(toCause(cause))
        }

    // RFC 6749 표준 token 에러는 top-level `error` 문자열. 파싱 실패·미존재 시 null → 호출부가 502 fallback.
    private fun readErrorCode(body: String): String? =
        runCatching {
            jsonMapper.readTree(body).path(FIELD_ERROR).let { node ->
                node.takeIf { it.isString }?.asString()
            }
        }.getOrElse { e ->
            (e as? JacksonException) ?: throw e // JSON 파싱 외 예외는 삼키지 않는다
            null
        }

    // cause 가 없으면(직접 분류 등) 분류 맥락을 잃지 않도록 합성 cause 를 만든다.
    private fun toCause(cause: Throwable?): Throwable = cause ?: IllegalStateException("Google OAuth 응답을 분류할 수 없습니다.")
}

/** Google OAuth 호출 종류. 같은 status·바디라도 token vs userinfo 에서 의미가 달라 분기 신호로 쓴다. */
enum class GoogleOAuthEndpoint {
    TOKEN,
    USER_INFO,
}
