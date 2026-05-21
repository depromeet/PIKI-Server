package com.depromeet.team3.ocr.service

import com.depromeet.team3.common.domain.Product
import com.depromeet.team3.ocr.domain.OcrImage

interface OcrClient {
    fun analyzeImage(image: OcrImage): Product
}
