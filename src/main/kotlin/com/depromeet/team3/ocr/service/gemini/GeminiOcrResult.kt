package com.depromeet.team3.ocr.service.gemini

import com.depromeet.team3.common.domain.BoundingBox
import com.depromeet.team3.common.domain.Product
import com.depromeet.team3.common.domain.Product.Field
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Gemini가 responseSchema에 따라 생성하는 JSON 문자열을 역직렬화한 결과.
 * <p>
 * 단일 상품만 받도록 스키마(PRODUCT_SCHEMA)를 OBJECT 타입으로 정의했음.
 */
data class GeminiOcrResult(
    val name: String?,
    val price: Int?,
    val category: String?,
    val nameBoundingBox: GeminiBoundingBox?,
    val priceBoundingBox: GeminiBoundingBox?,
    val categoryBoundingBox: GeminiBoundingBox?,
) {
    fun toProduct() = Product(
        name = toField(name, nameBoundingBox),
        price = toField(price, priceBoundingBox),
        category = toField(category, categoryBoundingBox),
    )

    private fun <T : Any> toField(value: T?, box: GeminiBoundingBox?): Field<T> {
        value ?: return Field.NotFound
        val boundingBox = box?.toBoundingBoxOrNull() ?: return Field.Inferred(value)
        return Field.Extracted(value, boundingBox)
    }

    /**
     * Gemini 응답의 bounding box. 스키마로 non-null 을 강제하긴 하지만,
     * 모델이 스키마를 100% 준수한다는 보장이 없고 부분 필드 누락/타입 깨짐이
     * 발생하면 전체 역직렬화가 실패할 수 있으므로 모든 필드를 nullable 로 받는다.
     * 유효성 검증과 도메인 객체 변환은 [toBoundingBoxOrNull] 에서 수행.
     */
    data class GeminiBoundingBox(
        @JsonProperty("ymin") val yMin: Int?,
        @JsonProperty("xmin") val xMin: Int?,
        @JsonProperty("ymax") val yMax: Int?,
        @JsonProperty("xmax") val xMax: Int?,
    ) {
        fun toBoundingBoxOrNull(): BoundingBox? =
            BoundingBox.ofOrNull(yMin = yMin, xMin = xMin, yMax = yMax, xMax = xMax)
    }
}
