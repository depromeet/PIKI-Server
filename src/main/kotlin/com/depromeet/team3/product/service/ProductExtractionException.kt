package com.depromeet.team3.product.service

import com.depromeet.team3.common.exception.BaseException
import com.depromeet.team3.common.exception.ErrorCategory
import com.depromeet.team3.common.exception.HttpMappable
import org.springframework.http.HttpStatus

class ProductExtractionException private constructor(
    message: String,
    override val category: ErrorCategory,
    override val httpStatus: HttpStatus,
) : BaseException(message),
    HttpMappable {
    companion object {
        // LLM 이 "상품 페이지가 아님"으로 판정. 링크 재등록·재시도 모두 무의미.
        fun notProductPage(): ProductExtractionException =
            ProductExtractionException(
                "상품 페이지가 아니라고 판단되어 등록할 수 없습니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )
    }
}
