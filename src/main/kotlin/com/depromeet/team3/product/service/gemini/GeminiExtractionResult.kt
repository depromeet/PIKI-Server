package com.depromeet.team3.product.service.gemini

import com.depromeet.team3.product.domain.CurrencyCode
import com.depromeet.team3.product.domain.ProductLink
import com.depromeet.team3.product.service.ProductSnapshot
import com.depromeet.team3.product.service.ProductSnapshotException

data class GeminiExtractionResult(
    val isProductPage: Boolean,
    val name: String? = null,
    val currentPrice: Int? = null,
    val currency: String? = null,
    val imageUrl: String? = null,
) {
    fun toProductSnapshot(link: ProductLink): ProductSnapshot {
        if (!isProductPage) throw ProductSnapshotException.notProductPage()

        val normalizedName = name?.takeIf { it.isNotBlank() }
        // LLM 이 javascript:/data: 같은 스킴을 흘리면 클라이언트가 <img src> 로 쓰는
        // 순간 XSS 사다리가 되므로 https 만 통과시킨다.
        val normalizedImageUrl = imageUrl?.takeIf { it.isNotBlank() && it.startsWith("https://", ignoreCase = true) }
        // LLM 이 형식을 제각각 주므로 ISO 4217 로 정규화하고, 안 맞으면 null (OCR 경로와 공유).
        val normalizedCurrency = CurrencyCode.normalizeOrNull(currency)

        // 추출 결과가 DB 컬럼 제약·상식을 벗어나면 추출 실패로 본다 (입력 경계의 계약 검증).
        if ((currentPrice ?: 0) < 0) {
            throw ProductSnapshotException.untrustworthyValue()
        }
        if ((normalizedName?.length ?: 0) > NAME_MAX_LENGTH) {
            throw ProductSnapshotException.untrustworthyValue()
        }
        if ((normalizedImageUrl?.length ?: 0) > IMAGE_URL_MAX_LENGTH) {
            throw ProductSnapshotException.untrustworthyValue()
        }

        return ProductSnapshot(
            link = link,
            name = normalizedName,
            imageUrl = normalizedImageUrl,
            currentPrice = currentPrice,
            currency = normalizedCurrency,
        )
    }

    companion object {
        // items 테이블 컬럼 길이와 일치시킨다.
        private const val NAME_MAX_LENGTH = 512
        private const val IMAGE_URL_MAX_LENGTH = 2048
    }
}
