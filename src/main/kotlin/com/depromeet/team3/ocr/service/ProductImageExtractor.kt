package com.depromeet.team3.ocr.service

import com.depromeet.team3.ocr.domain.OcrImage
import com.depromeet.team3.product.service.ProductSnapshot

interface ProductImageExtractor {
    fun extract(image: OcrImage): ProductSnapshot
}
