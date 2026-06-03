package com.depromeet.piki.auth.infrastructure.oauth.apple

import com.depromeet.piki.auth.infrastructure.oauth.OAuthException
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import tools.jackson.databind.json.JsonMapper

// Apple token endpoint(/auth/token) 의 HTTP 에러 응답을 시맨틱 기반으로 재매핑하는 순수 함수.
// Spring·DB 의존 없이 (status, body) 만으로 분류해 단위 테스트로 분기를 망라한다
// (CLAUDE.md 테스트 결정트리 (6) 외부 호출 결과 분기).
//
// 견고함의 3원칙 (PLAN.md):
// 1. 메시지 문자열에 의존 금지 — Apple ErrorResponse 는 안정 필드가 `error` 코드 하나뿐이고
//    `error_description` 은 미보장이라 의존하지 않는다.
// 2. provider HTTP status 그대로 전달 금지 — Apple 은 우리 설정 오류(invalid_client)도 400 으로 준다.
//    바디의 `error` 코드로 시맨틱 재매핑(→ 500 / 400)한다.
// 3. 미지의 코드 → 502 fallback — 파싱 실패·HTTP≠400·미지 error 코드는 안전하게 provider 장애로 본다.
//
// id_token(JWT) 검증 실패는 이 분류 대상이 아니다 — AppleOAuthClient 가 별도로 providerError(502) 로 처리한다.
object AppleOAuthErrorClassifier {
    // Apple token endpoint 의 표준 에러 코드. invalid_grant 만 클라가 정상 요청으로 도달 가능(계약)하고,
    // 나머지는 우리 client_secret JWT·요청 구성 오류(우리 설정 버그)다.
    private const val ERROR_INVALID_GRANT = "invalid_grant"
    private val MISCONFIGURED_ERROR_CODES =
        setOf(
            "invalid_client",
            "invalid_request",
            "unauthorized_client",
            "unsupported_grant_type",
            "invalid_scope",
        )

    // Apple token endpoint 는 에러를 전부 이 HTTP status 로 준다. 그 외 status 는 outage·장애 신호(매직넘버 대신 의미로).
    private val TOKEN_ERROR_HTTP_STATUS = HttpStatus.BAD_REQUEST.value()

    // Apple ErrorResponse 의 단일 안정 필드명.
    private const val FIELD_ERROR = "error"

    private val jsonMapper = JsonMapper.builder().build()

    // status·body 로 token 교환 에러를 분류한다. cause 는 디버깅용 원인(원래 던져진 예외)으로,
    // 502 fallback 에 한해 cause 체인을 보존한다.
    fun classify(
        status: HttpStatusCode,
        body: String,
        cause: Throwable,
    ): OAuthException {
        // Apple token endpoint 는 에러를 전부 HTTP 400 으로 준다. 그 외 status 는 Apple outage·장애 신호.
        if (status.value() != TOKEN_ERROR_HTTP_STATUS) return OAuthException.providerError(cause)

        val error = parseErrorCode(body) ?: return OAuthException.providerError(cause)

        if (error == ERROR_INVALID_GRANT) return OAuthException.invalidGrant()
        if (error in MISCONFIGURED_ERROR_CODES) return OAuthException.misconfigured(cause)

        // 문서에 없는 미지의 error 코드 → 안전하게 provider 장애로 fallback.
        return OAuthException.providerError(cause)
    }

    // Apple ErrorResponse 의 단일 `error` 필드만 추출한다. error_description 등 다른 필드엔 의존하지 않는다.
    // 파싱 실패(non-JSON·필드 부재)는 null 을 돌려 호출부가 502 fallback 으로 보낸다.
    private fun parseErrorCode(body: String): String? =
        runCatching { jsonMapper.readTree(body).get(FIELD_ERROR)?.asString() }
            .getOrNull()
}
