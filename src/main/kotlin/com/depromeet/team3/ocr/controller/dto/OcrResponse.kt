package com.depromeet.team3.ocr.controller.dto

import com.depromeet.team3.common.domain.Product

data class OcrResponse(
    val name: String?,
    val price: Int?,
    val category: String?,
) {
    companion object {
        fun from(product: Product) = OcrResponse(
            name = product.name,
            price = product.price,
            category = product.category,
        )
    }
}
