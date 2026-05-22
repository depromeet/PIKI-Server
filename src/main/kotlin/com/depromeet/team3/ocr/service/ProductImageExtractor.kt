package com.depromeet.team3.ocr.service

import com.depromeet.team3.ocr.domain.OcrImage

interface ProductImageExtractor {
    fun extract(image: OcrImage): OcrExtraction
}
