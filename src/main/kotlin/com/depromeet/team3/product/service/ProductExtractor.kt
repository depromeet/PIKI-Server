package com.depromeet.team3.product.service

import com.depromeet.team3.product.domain.ProductLink

interface ProductExtractor {
    fun extract(link: ProductLink): ProductDetails
}
