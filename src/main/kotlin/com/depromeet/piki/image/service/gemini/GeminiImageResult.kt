package com.depromeet.piki.image.service.gemini

import com.depromeet.piki.image.domain.BoundingBox
import com.depromeet.piki.image.service.ImageExtraction
import com.depromeet.piki.product.domain.CurrencyCode
import com.depromeet.piki.product.service.ProductSnapshot

/**
 * Gemini가 responseSchema에 따라 생성하는 JSON 문자열을 역직렬화한 결과.
 *
 * 단일 상품만 받도록 스키마(PRODUCT_SCHEMA)를 OBJECT 타입으로 정의했음.
 */
data class GeminiImageResult(
    val name: String?,
    val price: Int?,
    val category: String?,
    val currency: String?,
    val boundingBox: BoundingBoxDto? = null,
) {
    // Gemini 가 normalized 좌표(0~1000)로 주는 상품 영역. 네 좌표가 한 객체로 묶여 온다.
    data class BoundingBoxDto(
        val yMin: Int? = null,
        val xMin: Int? = null,
        val yMax: Int? = null,
        val xMax: Int? = null,
    )

    fun toImageExtraction(): ImageExtraction =
        ImageExtraction(
            snapshot = toProductSnapshot(),
            boundingBox =
                boundingBox?.let {
                    BoundingBox.ofNormalizedOrNull(yMin = it.yMin, xMin = it.xMin, yMax = it.yMax, xMax = it.xMax)
                },
        )

    // 이미지 추출 결과를 URL 추출과 같은 ProductSnapshot 으로 옮긴다. URL 이 없어 link 는 null,
    // category 는 현재 item 모델에 없어 버린다. price → currentPrice 로 어휘를 통일한다.
    // currency 는 LLM 이 형식을 제각각 주므로 ISO 4217 로 정규화하고, 안 맞으면 null.
    private fun toProductSnapshot() =
        ProductSnapshot(
            link = null,
            name = name,
            currentPrice = price,
            currency = CurrencyCode.normalizeOrNull(currency),
        )
}
