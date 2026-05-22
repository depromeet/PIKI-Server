package com.depromeet.team3.ocr.service.gemini

import com.depromeet.team3.ocr.domain.OcrImage
import com.depromeet.team3.ocr.service.ProductImageExtractor
import com.depromeet.team3.product.service.ProductSnapshot
import com.depromeet.team3.product.service.gemini.GeminiHttpClient
import org.springframework.stereotype.Component
import java.util.Base64

@Component
class GeminiProductImageExtractor(
    private val geminiHttpClient: GeminiHttpClient,
) : ProductImageExtractor {
    override fun extract(image: OcrImage): ProductSnapshot {
        val base64Image =
            Base64
                .getEncoder()
                .encodeToString(image.bytes)
        val request = GeminiOcrRequest.forImageAnalysis(base64Image, image.mimeType)
        return geminiHttpClient
            .generateContent(request, GeminiOcrResult::class.java)
            .toProductSnapshot()
    }
}
