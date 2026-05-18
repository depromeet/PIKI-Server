package com.depromeet.team3.ocr.service.gemini

import com.depromeet.team3.common.domain.Product

/**
 * Gemini가 responseSchema에 따라 생성하는 JSON 문자열을 역직렬화한 결과.
 *
 * 단일 상품만 받도록 스키마(PRODUCT_SCHEMA)를 OBJECT 타입으로 정의했음.
 */
data class GeminiOcrResult(
    val name: String?,
    val price: Int?,
    val category: String?,
) {
    fun toProduct() = Product(
        name = name,
        price = price,
        category = category,
    )
}
