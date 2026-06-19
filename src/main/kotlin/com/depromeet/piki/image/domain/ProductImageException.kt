package com.depromeet.piki.image.domain

import com.depromeet.piki.common.exception.BaseException
import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.exception.HttpMappable
import org.springframework.http.HttpStatus

// 업로드된 이미지 검증 실패를 나타내는 계약 예외. 빈 파일·미지정/미지원 형식은 모두 사용자가 올린 이미지로
// 정상 요청으로 도달 가능하므로(계약), require(불변식·500)가 아니라 커스텀 예외로 400 을 명시한다.
// status·category·message 가 예외 정의 한 곳에 모여, 호출 위치와 무관하게 같은 응답이 나온다.
class ProductImageException private constructor(
    message: String,
    override val category: ErrorCategory,
    override val httpStatus: HttpStatus,
) : BaseException(message),
    HttpMappable {
    companion object {
        fun emptyImage(): ProductImageException =
            ProductImageException("빈 이미지 파일은 올릴 수 없어요.", ErrorCategory.INVALID_INPUT, HttpStatus.BAD_REQUEST)

        fun unknownType(): ProductImageException =
            ProductImageException("이미지 형식을 확인할 수 없어요.", ErrorCategory.INVALID_INPUT, HttpStatus.BAD_REQUEST)

        fun unsupportedType(): ProductImageException =
            ProductImageException("지원하지 않는 이미지 형식이에요.", ErrorCategory.INVALID_INPUT, HttpStatus.BAD_REQUEST)
    }
}
