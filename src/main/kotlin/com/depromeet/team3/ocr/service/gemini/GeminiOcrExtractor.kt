package com.depromeet.team3.ocr.service.gemini

import com.depromeet.team3.common.domain.Product
import com.depromeet.team3.ocr.domain.OcrImage
import com.depromeet.team3.ocr.service.OcrExtractor
import com.depromeet.team3.product.service.gemini.GeminiHttpClient
import org.springframework.stereotype.Component
import java.util.Base64

@Component
class GeminiOcrExtractor(
    private val geminiHttpClient: GeminiHttpClient,
) : OcrExtractor {
    override fun extract(image: OcrImage): Product {
        val base64Image =
            Base64
                .getEncoder()
                .encodeToString(image.bytes)
        val request = GeminiOcrRequest.forImageAnalysis(base64Image, image.mimeType)
        return geminiHttpClient
            .generateContent(request, GeminiOcrResult::class.java)
            .toProduct()
    }
}
