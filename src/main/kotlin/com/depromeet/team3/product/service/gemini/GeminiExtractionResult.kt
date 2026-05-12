package com.depromeet.team3.product.service.gemini

import com.depromeet.team3.product.domain.ProductLink
import com.depromeet.team3.product.domain.ProductSnapshot
import com.depromeet.team3.product.service.ProductExtractionException

data class GeminiExtractionResult(
    val isProductPage: Boolean,
    val name: String? = null,
    val currentPrice: Int? = null,
    val currency: String? = null,
    val imageUrl: String? = null,
) {
    fun toProductSnapshot(link: ProductLink): ProductSnapshot {
        if (!isProductPage) throw ProductExtractionException.notProductPage()
        return ProductSnapshot(
            link = link,
            name = name?.takeIf { it.isNotBlank() },
            // LLM 이 javascript:/data: 같은 스킴을 흘리면 클라이언트가 <img src> 로 쓰는
            // 순간 XSS 사다리가 되므로 https 만 통과시킨다.
            imageUrl = imageUrl?.takeIf { it.isNotBlank() && it.startsWith("https://", ignoreCase = true) },
            currentPrice = currentPrice,
            currency = currency?.takeIf { it.isNotBlank() },
        )
    }
}
