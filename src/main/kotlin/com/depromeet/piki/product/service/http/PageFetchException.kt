package com.depromeet.piki.product.service.http

import com.depromeet.piki.common.exception.BaseException
import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.exception.HttpMappable
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
                "링크에 접근하지 못했어요. 주소를 다시 확인해 주세요.",
                ErrorCategory.RETRYABLE,
                HttpStatus.BAD_GATEWAY,
                cause,
            )

        // 4xx (404, 403 로그인 벽, 410 등). 입력 URL 자체가 문제이므로 사용자에게 400 으로 노출.
        fun clientError(cause: Throwable): PageFetchException =
            PageFetchException(
                "링크에 접근하지 못했어요. 주소를 다시 확인해 주세요.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
                cause,
            )

        fun emptyBody(): PageFetchException =
            PageFetchException(
                "해당 링크에서 정보를 가져오지 못했어요.",
                ErrorCategory.RETRYABLE,
                HttpStatus.BAD_GATEWAY,
            )

        // redirect 가 hop 상한을 넘어 무한·체인 의심. 대상 페이지의 고정된 비정상 상태라 재시도해도 결정론적으로
        // 재실패하므로 RETRYABLE(재시도 권유)이 아니라 SERVER_ERROR(재시도 불가). status 는 외부 의존성 실패라 502.
        fun tooManyRedirects(): PageFetchException =
            PageFetchException(
                "링크에 접근하지 못했어요. 주소를 다시 확인해 주세요.",
                ErrorCategory.SERVER_ERROR,
                HttpStatus.BAD_GATEWAY,
            )

        // 대상 서버가 3xx 를 주면서 Location 이 없거나 깨진 값을 준 비정상 redirect 응답. 재시도해도 영구 실패라 SERVER_ERROR.
        fun malformedRedirect(cause: Throwable? = null): PageFetchException =
            PageFetchException(
                "링크에 접근하지 못했어요. 주소를 다시 확인해 주세요.",
                ErrorCategory.SERVER_ERROR,
                HttpStatus.BAD_GATEWAY,
                cause,
            )

        // host 가 사설/메타데이터/loopback 영역으로 resolve 될 때 SSRF 차단 신호.
        fun blockedHost(): PageFetchException =
            PageFetchException(
                "등록할 수 없는 링크예요.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )
    }
}
