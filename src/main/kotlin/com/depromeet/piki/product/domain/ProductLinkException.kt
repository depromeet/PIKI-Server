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
                "링크를 입력해 주세요.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        // 원본 URL 은 message 에 박지 않는다. GlobalExceptionHandler 가 message 를 응답 detail·로그
        // 양쪽에 박는 구조라, 쿼리스트링/fragment 에 섞일 수 있는 토큰·세션이 외부로 새는 경로가 되기 때문.
        // 디버깅용 컨텍스트는 cause 로 연결해 stack trace 로만 남긴다.
        fun invalidFormat(cause: Throwable): ProductLinkException =
            ProductLinkException(
                "올바른 링크 형식이 아니에요. 다시 확인해 주세요.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
                cause,
            )

        fun unsupportedScheme(): ProductLinkException =
            ProductLinkException(
                "https 링크만 등록할 수 있어요.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        // fetch 로 상품 정보를 가져올 수 없는 플랫폼(직접 접근을 봇 차단하는 쇼핑몰)을 등록 시점에 거른다.
        // 어느 플랫폼인지는 message 에 박지 않는다(safeLogString 으로 로그). 사용자에겐 "아직 안 되는 곳" 안내만.
        fun unsupportedPlatform(): ProductLinkException =
            ProductLinkException(
                "아직 지원하지 않는 쇼핑몰이에요. 상품 이미지를 직접 등록해 주세요.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )
    }
}
