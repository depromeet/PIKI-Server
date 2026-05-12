package com.depromeet.team3.product.domain

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embeddable

@Embeddable
data class ProductSnapshot(
    @Convert(converter = ProductLinkConverter::class)
    @Column(name = "source_url", nullable = false, length = 2048)
    var link: ProductLink,
    @Column(name = "name", length = 512)
    var name: String? = null,
    @Column(name = "image_url", length = 2048)
    var imageUrl: String? = null,
    @Column(name = "current_price")
    var currentPrice: Int? = null,
    @Column(name = "currency", length = 8)
    var currency: String? = null,
)
