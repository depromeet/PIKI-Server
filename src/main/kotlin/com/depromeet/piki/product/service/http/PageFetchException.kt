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
    // 정적 fetch 가 "차단" 으로 막혔고 실제 브라우저(헤드리스)면 뚫릴 수 있는 신호인지 표시한다. FallbackProductLinkExtractor 가
    // 이 값으로 헤드리스 에스컬레이션 여부를 정한다(에스컬레이션 축은 outbox 재시도 축과 직교). 어떤 실패를 escalatable 로
    // 볼지는 던지는 지점(각 팩토리 = 단일 진실)이 정한다 — 현재는 403·500/501, provisional 이다. 기본 false. SSRF
    // 차단(blockedHost)은 뚫으면 안 되므로 false 로 둔다.
    val escalatable: Boolean = false,
) : BaseException(message, cause),
    HttpMappable {
    companion object {
        // 사용자 입력 URL 의 query/fragment 에 토큰·세션이 박혀 있을 수 있어 어떤 메시지에도
        // link 자체를 노출하지 않는다. 디버깅용 식별자는 호출 지점에서
        // link.safeLogString() (host + path 만 반환) 으로만 warn 로그를 남긴다.

        // 접근 실패는 사용자에겐 같은 안내라 한 상수를 공유한다(어느 단계 실패인지는 호출 지점 로그로 구분).
        private const val LINK_UNREACHABLE = "링크에 접근하지 못했어요. 주소를 다시 확인해 주세요."

        // 대상 페이지 서버가 502/503/504(게이트웨이 오류·과부하·타임아웃) 또는 연결 실패. 일시적일 수 있어
        // 재시도로 복구 가능성이 있다(RETRYABLE).
        fun upstreamError(cause: Throwable): PageFetchException =
            PageFetchException(LINK_UNREACHABLE, ErrorCategory.RETRYABLE, HttpStatus.BAD_GATEWAY, cause)

        // 대상 서버가 500/501 을 body 와 함께 준 경우 = 진짜 서버 장애(과부하·버그 등). 502/503/504(일시) 와 달리
        // 500/501 은 재시도해도 결정론적으로 재실패가 흔해 영구(SERVER_ERROR → 워커가 즉시 FAILED)로 본다. status 는 502.
        // escalatable=false — 헤드리스로 열어도 못 살리는 실제 장애라 escalate 하지 않는다.
        fun permanentUpstreamError(cause: Throwable): PageFetchException =
            PageFetchException(LINK_UNREACHABLE, ErrorCategory.SERVER_ERROR, HttpStatus.BAD_GATEWAY, cause)

        // 대상 서버가 500/501 을 body 없이 준 경우 = 봇 차단 신호(KREAM 등이 봇을 no-body 500 으로 응답). 영구(SERVER_ERROR)이되,
        // 실제 브라우저(헤드리스)면 뚫릴 수 있어 escalatable=true — Fallback 이 헤드리스로 에스컬레이트한다. (no-body 판별은 던지는 지점.)
        fun permanentUpstreamBlock(cause: Throwable): PageFetchException =
            PageFetchException(LINK_UNREACHABLE, ErrorCategory.SERVER_ERROR, HttpStatus.BAD_GATEWAY, cause, escalatable = true)

        // 4xx (404, 410 등, 403 제외). 입력 URL 자체가 문제이므로 사용자에게 400 으로 노출한다. escalatable=false —
        // 진짜 없는/잘못된 페이지라 헤드리스로 넘겨봐야 소용없다. 403 은 봇 차단일 수 있어 blocked() 로 따로 던진다.
        fun clientError(cause: Throwable): PageFetchException =
            PageFetchException(LINK_UNREACHABLE, ErrorCategory.INVALID_INPUT, HttpStatus.BAD_REQUEST, cause)

        // 403 (봇 차단·로그인 벽). 사용자 매핑은 4xx 와 같게 400 으로 두되, 봇 차단이면 실제 브라우저(헤드리스)로는
        // 뚫릴 수 있어 escalatable 로 표시한다 — Fallback 이 헤드리스 전략으로 에스컬레이트한다.
        fun blocked(cause: Throwable): PageFetchException =
            PageFetchException(LINK_UNREACHABLE, ErrorCategory.INVALID_INPUT, HttpStatus.BAD_REQUEST, cause, escalatable = true)

        fun emptyBody(): PageFetchException =
            PageFetchException(
                "해당 링크에서 정보를 가져오지 못했어요.",
                ErrorCategory.RETRYABLE,
                HttpStatus.BAD_GATEWAY,
            )

        // redirect 가 hop 상한을 넘어 무한·체인 의심. 대상 페이지의 고정된 비정상 상태라 재시도해도 결정론적으로
        // 재실패하므로 RETRYABLE(재시도 권유)이 아니라 SERVER_ERROR(재시도 불가). status 는 외부 의존성 실패라 502.
        fun tooManyRedirects(): PageFetchException =
            PageFetchException(LINK_UNREACHABLE, ErrorCategory.SERVER_ERROR, HttpStatus.BAD_GATEWAY)

        // 대상 서버가 3xx 를 주면서 Location 이 없거나 깨진 값을 준 비정상 redirect 응답. 재시도해도 영구 실패라 SERVER_ERROR.
        fun malformedRedirect(cause: Throwable? = null): PageFetchException =
            PageFetchException(LINK_UNREACHABLE, ErrorCategory.SERVER_ERROR, HttpStatus.BAD_GATEWAY, cause)

        // host 가 사설/메타데이터/loopback 영역으로 resolve 될 때 SSRF 차단 신호.
        fun blockedHost(): PageFetchException =
            PageFetchException(
                "등록할 수 없는 링크예요.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )
    }
}
