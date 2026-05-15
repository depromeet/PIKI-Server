package com.depromeet.team3.product.service

import com.depromeet.team3.product.domain.ProductLink

// 상품 페이지 추출 결과를 전달하는 DTO. 영속 표현(Item 엔티티)과 분리되어
// extract 가 트랜잭션·영속 컨텍스트 바깥에서 다뤄질 수 있게 한다.
data class ProductDetails(
    val link: ProductLink,
    val name: String? = null,
    val imageUrl: String? = null,
    val currentPrice: Int? = null,
    val currency: String? = null,
)
