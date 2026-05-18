package com.depromeet.team3.ocr.controller.dto

import com.depromeet.team3.common.domain.Product
import com.depromeet.team3.common.domain.Product.Field

data class OcrResponse(
    val name: ExtractedFieldResponse<String>?,
    val price: ExtractedFieldResponse<Int>?,
    val category: ExtractedFieldResponse<String>?,
) {
    /**
     * boundingBox 는 0~1000 정규화된 좌표(yMin, xMin, yMax, xMax).
     * boundingBox 가 비어 있으면 추론된 값(이미지에서 직접 읽은 것이 아님).
     */
    data class ExtractedFieldResponse<T>(
        val value: T,
        val boundingBox: BoundingBoxResponse?,
        val isInferred: Boolean,
    )

    data class BoundingBoxResponse(
        val yMin: Int,
        val xMin: Int,
        val yMax: Int,
        val xMax: Int,
    )

    companion object {
        fun from(product: Product) = OcrResponse(
            name = product.name.toResponse(),
            price = product.price.toResponse(),
            category = product.category.toResponse(),
        )

        private fun <T> Field<T>.toResponse(): ExtractedFieldResponse<T>? = when (this) {
            is Field.NotFound -> null
            is Field.Inferred -> ExtractedFieldResponse(
                value = value,
                boundingBox = null,
                isInferred = true,
            )

            is Field.Extracted -> ExtractedFieldResponse(
                value = value,
                boundingBox = BoundingBoxResponse(
                    yMin = boundingBox.yMin,
                    xMin = boundingBox.xMin,
                    yMax = boundingBox.yMax,
                    xMax = boundingBox.xMax,
                ),
                isInferred = false,
            )
        }
    }
}
