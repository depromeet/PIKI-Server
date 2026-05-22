package com.depromeet.team3.product.service

import com.depromeet.team3.product.domain.ProductLink

// 상품 추출 시점의 상태를 캡처한 결과. URL 추출(link)·이미지 추출(OCR) 두 경로가 공유하는 표현이며,
// 영속 표현(Item 엔티티)과 분리되어 extract 가 트랜잭션·영속 컨텍스트 바깥에서 다뤄질 수 있게 한다.
// OCR 추출은 URL 이 없어 link 가 null 이다.
data class ProductSnapshot(
    val link: ProductLink? = null,
    val name: String? = null,
    val imageUrl: String? = null,
    val currentPrice: Int? = null,
    val currency: String? = null,
)
