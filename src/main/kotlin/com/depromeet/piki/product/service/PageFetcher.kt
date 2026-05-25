package com.depromeet.piki.product.service

import com.depromeet.piki.product.domain.ProductLink

interface PageFetcher {
    fun fetch(link: ProductLink): PageContent
}
