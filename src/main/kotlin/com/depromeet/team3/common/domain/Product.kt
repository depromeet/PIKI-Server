package com.depromeet.team3.common.domain

// OCR 로 추출한 상품 정보. 각 필드는 추출에 실패하면 null.
data class Product(
    val name: String?,
    val price: Int?,
    val category: String?,
    // price 의 통화 (ISO 4217, 예: KRW/USD). 통화 없는 가격은 의미가 불완전하므로 함께 추출한다.
    val currency: String? = null,
)
