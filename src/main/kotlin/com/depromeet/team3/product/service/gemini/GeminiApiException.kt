package com.depromeet.team3.product.service.gemini

import com.depromeet.team3.common.exception.BaseException
import com.depromeet.team3.common.exception.ErrorCategory
import com.depromeet.team3.common.exception.HttpMappable
import org.springframework.http.HttpStatus

class GeminiApiException private constructor(
    message: String,
    override val category: ErrorCategory,
    cause: Throwable? = null,
) : BaseException(message, cause),
    HttpMappable {
    override val httpStatus: HttpStatus = HttpStatus.BAD_GATEWAY

    companion object {
        fun upstreamError(cause: Throwable): GeminiApiException =
            GeminiApiException("Gemini 호출 실패", ErrorCategory.RETRYABLE, cause)

        fun clientError(cause: Throwable): GeminiApiException =
            GeminiApiException("Gemini 요청 오류", ErrorCategory.SERVER_ERROR, cause)

        // body 자체가 없음 (역직렬화 이전). transport/인프라 이슈로 일시적일 가능성이 크므로 RETRYABLE.
        fun emptyResponse(): GeminiApiException = GeminiApiException("Gemini 응답이 비어 있습니다.", ErrorCategory.RETRYABLE)

        fun parseError(cause: Throwable): GeminiApiException =
            GeminiApiException("Gemini 응답 처리 실패", ErrorCategory.SERVER_ERROR, cause)

        // 스키마는 유효하지만 candidates/parts 가 빈 리스트. safety filter 등 정책적 거부 가능성이 높아 재시도 무의미.
        fun noTextPart(): GeminiApiException =
            GeminiApiException("Gemini 응답에 텍스트 파트가 없습니다.", ErrorCategory.SERVER_ERROR)
    }
}
