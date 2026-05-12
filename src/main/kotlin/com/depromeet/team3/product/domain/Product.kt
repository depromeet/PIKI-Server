package com.depromeet.team3.product.domain

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embeddable

@Embeddable
data class Product(
    @Convert(converter = ProductLinkConverter::class)
    @Column(name = "source_url", nullable = false, length = 2048)
    var link: ProductLink,
    @Column(name = "name", length = 512)
    var name: String? = null,
    @Column(name = "image_url", length = 2048)
    var imageUrl: String? = null,
    @Column(name = "regular_price")
    var regularPrice: Int? = null,
    @Column(name = "discounted_price")
    var discountedPrice: Int? = null,
    @Column(name = "currency", length = 8)
    var currency: String? = null,
) {
    val discountRate: Int?
        get() {
            val regular = regularPrice ?: return null
            val discounted = discountedPrice ?: return null
            if (regular <= 0 || discounted >= regular) return null
            return ((regular - discounted) * 100.0 / regular).toInt()
        }
}
