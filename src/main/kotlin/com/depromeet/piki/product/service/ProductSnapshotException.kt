package com.depromeet.piki.product.service

import com.depromeet.piki.common.exception.BaseException
import com.depromeet.piki.common.exception.ErrorCategory
import com.depromeet.piki.common.exception.HttpMappable
import org.springframework.http.HttpStatus

class ProductSnapshotException private constructor(
    message: String,
    override val category: ErrorCategory,
    override val httpStatus: HttpStatus,
) : BaseException(message),
    HttpMappable {
    companion object {
        // LLM 이 "상품 페이지가 아님"으로 판정. 링크 재등록·재시도 모두 무의미.
        fun notProductPage(): ProductSnapshotException =
            ProductSnapshotException(
                "상품 페이지가 아니라고 판단되어 등록할 수 없습니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )

        // LLM 추출값이 유효 범위(가격 음수, 컬럼 길이 초과 등)를 벗어남. 추출 결과를 신뢰할 수 없다.
        // 정상 URL 이라도 LLM 이 비결정적으로 이상값을 낼 수 있어 클라이언트가 닿는 계약 예외다.
        // message 는 응답 detail 로 노출되므로 구체 사유를 담지 않고 고정 문구로 둔다.
        fun untrustworthyValue(): ProductSnapshotException =
            ProductSnapshotException(
                "추출된 상품 정보를 신뢰할 수 없어 등록할 수 없습니다.",
                ErrorCategory.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
            )
    }
}
