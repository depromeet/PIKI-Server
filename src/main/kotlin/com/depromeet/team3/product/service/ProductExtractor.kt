package com.depromeet.team3.product.service

import com.depromeet.team3.product.domain.ProductLink
import com.depromeet.team3.product.domain.ProductSnapshot

interface ProductExtractor {
    fun extract(link: ProductLink): ProductSnapshot
}
