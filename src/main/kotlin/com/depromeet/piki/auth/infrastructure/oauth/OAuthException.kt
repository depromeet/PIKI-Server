package com.depromeet.piki.auth.infrastructure.oauth

import com.depromeet.piki.common.exception.BaseException
import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.exception.HttpMappable
import org.springframework.http.HttpStatus

class OAuthException private constructor(
    message: String,
    override val category: ErrorCategory,
    override val httpStatus: HttpStatus,
    cause: Throwable? = null,
) : BaseException(message, cause),
    HttpMappable {
    companion object {
        // 소셜 제공자(Kakao/Google) 호출 실패 — 우리 밖 의존성. 정상 요청이어도 도달 가능한 계약 → 502.
        // 디버깅용 원인은 cause 로만 남기고 message 는 고정 문구(원문 노출 금지).
        fun providerError(cause: Throwable): OAuthException =
            OAuthException("소셜 로그인 제공자 호출에 실패했습니다.", ErrorCategory.RETRYABLE, HttpStatus.BAD_GATEWAY, cause)

        // code(+redirectUri) 도 accessToken 도 없어 어느 흐름도 성립 안 함 → 400.
        fun invalidRequest(): OAuthException =
            OAuthException(
                "소셜 로그인 요청이 올바르지 않습니다 (code+redirectUri 또는 accessToken 이 필요합니다).",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        // 지원하지 않는 provider (미구현 apple · 오타 등) → 400.
        fun unsupportedProvider(): OAuthException =
            OAuthException("지원하지 않는 소셜 로그인 제공자입니다.", ErrorCategory.INVALID_INPUT, HttpStatus.BAD_REQUEST)

        // state 없음 · 만료 · 이미 소비됨 → 401. CSRF 방지용 state 불일치로 요청을 거부.
        fun invalidState(): OAuthException =
            OAuthException("유효하지 않은 state 파라미터입니다. 인가 URL 을 새로 발급받아 다시 시도하세요.", ErrorCategory.UNAUTHORIZED, HttpStatus.UNAUTHORIZED)

        // provider 인가코드(code)가 만료/재사용/무효 — 멀쩡한 클라가 정상 요청으로 도달 가능(계약) → 400.
        // 재로그인으로 새 code 를 받아 재시도하면 해소된다. 원문은 cause 로만, message 는 고정 문구.
        fun invalidGrant(): OAuthException =
            OAuthException(
                "소셜 로그인 인가 정보가 만료되었거나 유효하지 않습니다. 다시 시도해 주세요.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        // provider access token 무효/만료 — 클라가 정상 요청으로 도달 가능(계약) → 401.
        // 재로그인으로 새 토큰을 받아야 해소된다. 원문은 cause 로만, message 는 고정 문구.
        fun invalidProviderToken(): OAuthException =
            OAuthException(
                "소셜 로그인 토큰이 유효하지 않습니다. 다시 로그인해 주세요.",
                ErrorCategory.UNAUTHORIZED,
                HttpStatus.UNAUTHORIZED,
            )

        // 우리 OAuth 설정/요청 오류(client_id/secret · client_secret JWT · 필수 인자 누락 등) — 외부 호출 경계에서
        // provider 가 "네 자격/요청이 틀렸다"고 거부한 것. GeminiApiException.clientError 와 같은 결로 502 + SERVER_ERROR:
        // status 502 = 외부 호출 경계 실패, category SERVER_ERROR = 재시도해도 무의미한 우리 서버 문제(알림 신호).
        // 정상 클라 요청으로 해소 불가. 원인은 cause 로만 보존하고 message 는 고정 문구(원문 비노출).
        fun misconfigured(cause: Throwable): OAuthException =
            OAuthException(
                "소셜 로그인 설정 오류가 발생했습니다.",
                ErrorCategory.SERVER_ERROR,
                HttpStatus.BAD_GATEWAY,
                cause,
            )
    }
}
