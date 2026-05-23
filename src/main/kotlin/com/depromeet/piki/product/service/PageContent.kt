package com.depromeet.piki.product.service

import com.depromeet.piki.product.domain.ProductLink

data class PageContent(
    val link: ProductLink,
    val html: String,
)
