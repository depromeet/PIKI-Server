package com.depromeet.piki.ocr.service.gemini

import com.depromeet.piki.ocr.domain.OcrImage
import com.depromeet.piki.ocr.service.ProductImageExtractor
import com.depromeet.piki.product.service.ProductSnapshot
import com.depromeet.piki.product.service.gemini.GeminiHttpClient
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
