package com.depromeet.piki.product.service

import com.depromeet.piki.product.domain.CurrencyCode
import com.depromeet.piki.product.domain.ProductLink

// 상품 추출 시점의 상태를 캡처한 결과. URL 추출(link)·이미지 추출(image) 두 경로가 공유하는 표현이며,
// 영속 표현(Item 엔티티)과 분리되어 extract 가 트랜잭션·영속 컨텍스트 바깥에서 다뤄질 수 있게 한다.
// 이미지 추출은 URL 이 없어 link 가 null 이다.
data class ProductSnapshot(
    val link: ProductLink? = null,
    val name: String? = null,
    val imageUrl: String? = null,
    val currentPrice: Int? = null,
    val currency: String? = null,
) {
    companion object {
        // items 테이블 컬럼 길이와 일치시킨다.
        private const val NAME_MAX_LENGTH = 512
        private const val IMAGE_URL_MAX_LENGTH = 2048

        // 원시 추출값(구조화 파싱·LLM 추출이 공유)을 정규화·범위검증해 만드는 단일 진실 원천.
        // name blank→null, imageUrl 은 https 만(클라이언트가 <img src> 로 쓸 때의 XSS 사다리 차단),
        // currency 는 ISO 4217 로 정규화한다. 추출값이 DB 컬럼 제약·상식을 벗어나면(가격 음수·길이 초과)
        // 추출 실패로 보고 untrustworthyValue 를 던진다 — 입력 경계의 계약 검증.
        //
        // 실패 처리는 호출부가 고른다: 구조화 경로는 이 예외를 runCatching 으로 흡수해 null(→LLM fallback)로,
        // LLM 경로는 그대로 흘려 FAILED 로 떨어뜨린다. 같은 검증, 실패 표현만 다르다.
        fun fromExtracted(
            link: ProductLink?,
            name: String?,
            imageUrl: String?,
            currentPrice: Int?,
            currency: String?,
        ): ProductSnapshot {
            val normalizedName = name?.takeIf { it.isNotBlank() }
            val normalizedImageUrl =
                imageUrl?.takeIf { it.isNotBlank() && it.startsWith("https://", ignoreCase = true) }
            val normalizedCurrency = CurrencyCode.normalizeOrNull(currency)

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
    }
}
