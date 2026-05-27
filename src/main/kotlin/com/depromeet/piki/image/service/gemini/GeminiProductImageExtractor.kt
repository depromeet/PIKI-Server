package com.depromeet.piki.image.service.gemini

import com.depromeet.piki.image.domain.ProductImage
import com.depromeet.piki.image.service.ImageExtraction
import com.depromeet.piki.image.service.ProductImageExtractor
import com.depromeet.piki.product.service.gemini.GeminiHttpClient
import org.springframework.stereotype.Component
import java.util.Base64

@Component
class GeminiProductImageExtractor(
    private val geminiHttpClient: GeminiHttpClient,
) : ProductImageExtractor {
    override fun extract(image: ProductImage): ImageExtraction {
        val base64Image =
            Base64
                .getEncoder()
                .encodeToString(image.bytes)
        val request = GeminiImageRequest.forImageAnalysis(base64Image, image.mimeType)
        return geminiHttpClient
            .generateContent(request, GeminiImageResult::class.java)
            .toImageExtraction()
    }
}
