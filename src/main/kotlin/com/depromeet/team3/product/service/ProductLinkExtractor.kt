package com.depromeet.team3.product.service

import com.depromeet.team3.product.domain.ProductLink

interface ProductLinkExtractor {
    fun extract(link: ProductLink): ProductSnapshot
}
