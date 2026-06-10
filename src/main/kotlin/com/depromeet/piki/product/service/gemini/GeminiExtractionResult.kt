package com.depromeet.piki.product.service.gemini

import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.product.service.ProductSnapshot
import com.depromeet.piki.product.service.ProductSnapshotException

data class GeminiExtractionResult(
    val isProductPage: Boolean,
    val name: String? = null,
    val currentPrice: Int? = null,
    val currency: String? = null,
    val imageUrl: String? = null,
) {
    // LLM 고유의 "상품 페이지인가" 판정만 여기서 하고, 정규화·범위검증은 ProductSnapshot.fromExtracted 에 위임한다.
    // (구조화 파싱 경로와 같은 검증을 공유하는 single source.)
    fun toProductSnapshot(link: ProductLink): ProductSnapshot {
        if (!isProductPage) throw ProductSnapshotException.notProductPage()
        return ProductSnapshot.fromExtracted(
            link = link,
            name = name,
            imageUrl = imageUrl,
            currentPrice = currentPrice,
            currency = currency,
        )
    }
}
