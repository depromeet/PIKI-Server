package com.depromeet.piki.auth.infrastructure.oauth

import com.depromeet.piki.common.exception.ErrorCategory
import org.slf4j.Logger

// provider(Google·Kakao·Apple) 의 OAuth 에러 응답을 관측 가능하게 남긴다.
//
// 왜 필요한가: 분류기는 우리가 아는 코드만 정밀 매핑하고 문서에 없는(혹은 provider 가 새로 추가한) 코드는
// 안전하게 502 RETRYABLE 로 fallback 한다. 그런데 그 원본을 로그로 남기지 않으면 미지 코드가 조용히 502 로
// 묻혀 우리가 영영 알 수 없다(GlobalExceptionHandler 는 고정 message 만 info 로 찍고 원본 body·cause 는 안 남긴다).
// 이 함수가 provider 원본을 남겨, 새 코드가 뜨면 우리가 보고 분류 set 에 추가(또는 alert)할 수 있게 한다.
//
// 레벨은 분류 결과 category 를 따른다 (CLAUDE.md 로깅 레벨):
//   - 클라 자격/요청 오류(INVALID_INPUT·UNAUTHORIZED): 서버 입장에선 정상 동작 → info.
//   - 우리 설정/요청 오류·provider 장애·미지 코드 fallback(SERVER_ERROR·RETRYABLE): 외부 호출 실패 → warn.
//
// body 는 provider 의 "에러 응답" 이라 access token 등 자격증명을 echo 하지 않아(요청이 아니라 응답) 로깅이 안전하다.
// 다만 로그 폭주를 막기 위해 길이를 제한한다.
private const val MAX_BODY_LOG_LENGTH = 500

fun logOAuthProviderError(
    log: Logger,
    provider: String,
    endpoint: String,
    status: Int,
    body: String,
    category: ErrorCategory,
) {
    val snippet = body.take(MAX_BODY_LOG_LENGTH)
    when (category) {
        ErrorCategory.INVALID_INPUT, ErrorCategory.UNAUTHORIZED ->
            log.info("[OAuth {} {}] 클라 자격/요청 오류 status={} category={} body={}", provider, endpoint, status, category, snippet)
        else ->
            log.warn(
                "[OAuth {} {}] provider 에러 (미지 코드면 502 fallback — 분류 set 추가 검토 대상) status={} category={} body={}",
                provider,
                endpoint,
                status,
                category,
                snippet,
            )
    }
}
