package com.depromeet.piki.ocr.service

import com.depromeet.piki.ocr.domain.OcrImage

interface ProductImageExtractor {
    fun extract(image: OcrImage): OcrExtraction
}
