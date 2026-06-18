package com.depromeet.piki.common.imageproxy

import com.depromeet.piki.common.exception.BaseException
import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.exception.HttpMappable
import org.springframework.http.HttpStatus

class ImageProxyException private constructor(
    message: String,
    override val category: ErrorCategory,
    override val httpStatus: HttpStatus,
) : BaseException(message),
    HttpMappable {
    companion object {
        fun blockedDomain(): ImageProxyException =
            ImageProxyException(
                "허용되지 않은 이미지 도메인입니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        fun imageTooLarge(): ImageProxyException =
            ImageProxyException(
                "이미지 크기가 너무 큽니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        fun fetchFailed(): ImageProxyException =
            ImageProxyException(
                "이미지를 불러올 수 없습니다.",
                ErrorCategory.RETRYABLE,
                HttpStatus.BAD_GATEWAY,
            )
    }
}
