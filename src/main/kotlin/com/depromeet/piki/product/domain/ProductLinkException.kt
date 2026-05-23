package com.depromeet.piki.product.domain

import com.depromeet.piki.common.exception.BaseException
import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.exception.HttpMappable
import org.springframework.http.HttpStatus

class ProductLinkException private constructor(
    message: String,
    override val category: ErrorCategory,
    override val httpStatus: HttpStatus,
    cause: Throwable? = null,
) : BaseException(message, cause),
    HttpMappable {
    companion object {
        fun blank(): ProductLinkException =
            ProductLinkException(
                "URL이 비어 있습니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        // 원본 URL 은 message 에 박지 않는다. GlobalExceptionHandler 가 message 를 응답 detail·로그
        // 양쪽에 박는 구조라, 쿼리스트링/fragment 에 섞일 수 있는 토큰·세션이 외부로 새는 경로가 되기 때문.
        // 디버깅용 컨텍스트는 cause 로 연결해 stack trace 로만 남긴다.
        fun invalidFormat(cause: Throwable): ProductLinkException =
            ProductLinkException(
                "유효한 URL 형식이 아닙니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
                cause,
            )

        fun unsupportedScheme(): ProductLinkException =
            ProductLinkException(
                "https URL만 허용합니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )
    }
}
