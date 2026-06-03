package com.depromeet.piki.auth.infrastructure.oauth.kakao

import com.depromeet.piki.auth.infrastructure.oauth.OAuthException
import org.springframework.http.HttpStatusCode
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper

// Kakao OAuth 에러 응답을 우리 시맨틱(400/401/502)으로 분류하는 순수 함수 모음.
//
// 견고함의 3원칙(PLAN.md):
//   1. 메시지 문자열(error_description·msg)에 의존하지 않는다 — 안정 필드(KOE error_code · 정수 code)로만 분기.
//   2. provider HTTP status 를 그대로 전달하지 않는다 — Kakao 는 장애(-1)도 HTTP 400 으로 주므로 시맨틱 재매핑.
//   3. 미지의 코드는 502 로 fallback — 문서에 없는 코드가 와도 안 깨진다.
//
// token endpoint 와 user API 는 바디 포맷이 다르다(전자는 문자열 error_code, 후자는 정수 code)므로
// endpoint 별로 분리된 분류 메서드를 둔다.
object KakaoOAuthErrorClassifier {
    // 파싱 실패·필드 누락에도 안 깨지게 readTree 로 방어적으로 읽는다.
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    // --- 안정 분기 신호 (provider 가 정해 보내는 고정값) ---
    // token endpoint: 문자열 error_code(KOE...)
    private const val INVALID_AUTH_CODE = "KOE320" // 인가코드 무효/만료/재사용 (클라) → 400
    private val MISCONFIGURED_TOKEN_ERROR_CODES = setOf("KOE101", "KOE010", "KOE303", "KOE114", "KOE310") // 우리 설정 오류 → 502/SERVER_ERROR
    private const val TRANSIENT_TOKEN_ERROR_CODE = "KOE003" // 카카오 OAuth 서버 일시 오류 → 502/RETRYABLE

    // user API: 음수 정수 code
    private const val INVALID_ACCESS_TOKEN_CODE = -401 // access_token 무효/만료 (클라) → 401
    private val TRANSIENT_API_CODES = setOf(-1, -7, -9798) // provider 장애/점검 → 502/RETRYABLE
    private val MISCONFIGURED_API_CODES = setOf(-2, -8) // 우리 요청 구성 버그(필수 인자 누락·헤더 오류) → 502/SERVER_ERROR

    // JSON 안정 필드명
    private const val FIELD_ERROR_CODE = "error_code"
    private const val FIELD_CODE = "code"

    // token endpoint 응답 바디는 { error, error_description, error_code(KOE...) } 형태.
    fun classifyTokenError(
        status: HttpStatusCode,
        body: String,
    ): OAuthException {
        val errorCode = readErrorCode(body) ?: return OAuthException.providerError(cause(status, body))
        return when (errorCode) {
            // 인가코드 무효/만료/재사용 — 멀쩡한 클라가 정상 요청으로 도달 가능(계약) → 400.
            INVALID_AUTH_CODE -> OAuthException.invalidGrant()
            // 우리 설정 오류(REST 키·secret·redirect 불일치 등) → 502/SERVER_ERROR.
            in MISCONFIGURED_TOKEN_ERROR_CODES -> OAuthException.misconfigured(cause(status, body))
            // 카카오 OAuth 서버 일시 오류 → 502/RETRYABLE. (미지 KOE 도 아래 else 에서 동일하게 502 RETRYABLE)
            TRANSIENT_TOKEN_ERROR_CODE -> OAuthException.providerError(cause(status, body))
            // 그 외/미지 KOE — 보수적으로 502 로 fallback.
            else -> OAuthException.providerError(cause(status, body))
        }
    }

    // user API 응답 바디는 { msg, code(음수) } 형태. msg 는 가변이라 분기에 쓰지 않고 정수 code 로만 분기.
    fun classifyUserApiError(
        status: HttpStatusCode,
        body: String,
    ): OAuthException {
        val code = readIntCode(body) ?: return OAuthException.providerError(cause(status, body))
        return when (code) {
            // access_token 무효/만료 — 클라가 정상 요청으로 도달 가능(계약) → 401.
            INVALID_ACCESS_TOKEN_CODE -> OAuthException.invalidProviderToken()
            // provider 장애(일시 오류·점검) → 502/RETRYABLE(재시도 대상).
            in TRANSIENT_API_CODES -> OAuthException.providerError(cause(status, body))
            // 우리 요청 구성 버그(필수 인자 누락·헤더 오류) → 502/SERVER_ERROR.
            in MISCONFIGURED_API_CODES -> OAuthException.misconfigured(cause(status, body))
            // 미지의 음수 code — 보수적으로 502 로 fallback.
            else -> OAuthException.providerError(cause(status, body))
        }
    }

    // KOE error_code(문자열)를 안전하게 추출. 없거나 파싱 실패 시 null.
    private fun readErrorCode(body: String): String? {
        val node = parse(body) ?: return null
        val errorCode = node.get(FIELD_ERROR_CODE) ?: return null
        return errorCode.asString().ifBlank { null }
    }

    // 정수 code 를 안전하게 추출. 정수가 아니거나 없으면 null.
    private fun readIntCode(body: String): Int? {
        val node = parse(body) ?: return null
        val code = node.get(FIELD_CODE) ?: return null
        return code.takeIf { it.canConvertToInt() }?.asInt()
    }

    private fun parse(body: String): JsonNode? =
        runCatching { objectMapper.readTree(body) }.getOrNull()

    // 원문 바디는 응답에 노출하지 않고 cause 로만 보존한다(내부 정보 비노출 컨벤션).
    private fun cause(
        status: HttpStatusCode,
        body: String,
    ): Throwable = IllegalStateException("Kakao OAuth error response (status=$status): $body")
}
