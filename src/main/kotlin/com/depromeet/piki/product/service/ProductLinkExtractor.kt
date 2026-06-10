package com.depromeet.piki.product.service

import com.depromeet.piki.product.domain.ProductLink

interface ProductLinkExtractor {
    fun extract(link: ProductLink): ProductSnapshot
}
