package com.depromeet.team3.common.domain

// OCR 로 추출한 상품 정보. 각 필드는 추출에 실패하면 null.
data class Product(
    val name: String?,
    val price: Int?,
    val category: String?,
)
