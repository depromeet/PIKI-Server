package com.depromeet.piki.image.service

import com.depromeet.piki.image.domain.ProductImage

interface ProductImageExtractor {
    fun extract(image: ProductImage): ImageExtraction
}
