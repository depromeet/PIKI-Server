package com.depromeet.piki.ocr.service

import com.depromeet.piki.ocr.domain.OcrImage
import com.depromeet.piki.product.service.ProductSnapshot

interface ProductImageExtractor {
    fun extract(image: OcrImage): ProductSnapshot
}
