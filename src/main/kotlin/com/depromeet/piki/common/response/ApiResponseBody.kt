package com.depromeet.piki.common.response

import com.depromeet.piki.common.exception.ErrorCategory
import org.slf4j.MDC

data class ApiResponseBody<T>(
    val data: T?,
    val detail: String,
    // 요청 추적 ID(Micrometer Tracing 의 traceId). 모든 응답에 실려, Grafana Loki 에서 같은 값으로
    // 그 요청의 전체 로그를 검색할 수 있다. trace 컨텍스트 밖(필터 이전 등)에서 만들어지면 null.
    val traceId: String?,
    // 모든 응답이 동일한 형태를 갖도록 항상 포함한다. 페이징과 무관한 응답은 "다음 페이지 없음"(EMPTY) 으로 채운다.
    val pageResponse: PageResponse = PageResponse.EMPTY,
) {
    companion object {
        private const val SUCCESS_DETAIL = "정상적으로 처리되었습니다."

        fun <T> ok(
            data: T? = null,
            pageResponse: PageResponse = PageResponse.EMPTY,
        ): ApiResponseBody<T> = ApiResponseBody(data, SUCCESS_DETAIL, currentTraceId(), pageResponse)

        fun <T> created(data: T? = null): ApiResponseBody<T> =
            ApiResponseBody(data, SUCCESS_DETAIL, currentTraceId())

        fun <T> fail(
            category: ErrorCategory,
            detail: String? = null,
        ): ApiResponseBody<T> =
            ApiResponseBody(
                data = null,
                detail = detail ?: category.description,
                traceId = currentTraceId(),
            )

        // Micrometer Tracing(Brave)이 요청 스코프에서 MDC 에 넣어 둔 traceId 를 읽는다. 모든 응답 생성
        // 경로(컨트롤러·예외 핸들러·Security 핸들러)가 이 팩토리를 거치므로 traceId 가 일관되게 채워진다.
        private fun currentTraceId(): String? = MDC.get("traceId")
    }
}
