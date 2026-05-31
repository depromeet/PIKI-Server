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
    }
}
