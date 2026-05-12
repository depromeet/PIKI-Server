package com.depromeet.team3.product.service.http

import com.depromeet.team3.common.exception.BaseException
import com.depromeet.team3.common.exception.ErrorCategory
import com.depromeet.team3.common.exception.HttpMappable
import org.springframework.http.HttpStatus

class PageFetchException private constructor(
    message: String,
    override val category: ErrorCategory,
    override val httpStatus: HttpStatus,
    cause: Throwable? = null,
) : BaseException(message, cause),
    HttpMappable {
    companion object {
        // 사용자 입력 URL 의 query/fragment 에 토큰·세션이 박혀 있을 수 있어 어떤 메시지에도
        // link 자체를 노출하지 않는다. 디버깅용 식별자는 호출 지점에서
        // link.safeLogString() (host + path 만 반환) 으로만 warn 로그를 남긴다.

        // 대상 페이지 서버가 5xx 또는 연결 실패. 재시도로 복구 가능성 있음.
        fun upstreamError(cause: Throwable): PageFetchException =
            PageFetchException(
                "링크 페이지 호출에 실패했습니다.",
                ErrorCategory.RETRYABLE,
                HttpStatus.BAD_GATEWAY,
                cause,
            )

        // 4xx (404, 403 로그인 벽, 410 등). 입력 URL 자체가 문제이므로 사용자에게 400 으로 노출.
        fun clientError(cause: Throwable): PageFetchException =
            PageFetchException(
                "링크 페이지에 접근할 수 없습니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
                cause,
            )

        fun emptyBody(): PageFetchException =
            PageFetchException(
                "링크 페이지 응답이 비어 있습니다.",
                ErrorCategory.RETRYABLE,
                HttpStatus.BAD_GATEWAY,
            )

        // host 가 사설/메타데이터/loopback 영역으로 resolve 될 때 SSRF 차단 신호.
        fun blockedHost(): PageFetchException =
            PageFetchException(
                "허용되지 않는 호스트입니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )
    }
}
