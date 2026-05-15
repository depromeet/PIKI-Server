package com.depromeet.team3.item.domain

import com.depromeet.team3.product.domain.ProductLink
import com.depromeet.team3.product.service.ProductDetails
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ItemTest {
    private val link = ProductLink.parse("https://shop.example.com/products/42")

    @Test
    fun `ProductDetails 의 전 필드가 Item 으로 매핑된다`() {
        val details =
            ProductDetails(
                link = link,
                name = "나이키 에어포스",
                imageUrl = "https://cdn.example.com/p/42.jpg",
                currentPrice = 99_000,
                currency = "KRW",
            )

        val item = Item.from(details)

        assertEquals(link, item.link)
        assertEquals("나이키 에어포스", item.name)
        assertEquals("https://cdn.example.com/p/42.jpg", item.imageUrl)
        assertEquals(99_000, item.currentPrice)
        assertEquals("KRW", item.currency)
    }

    @Test
    fun `link 만 있는 ProductDetails 도 Item 으로 매핑되며 나머지는 null 로 보존된다`() {
        val item = Item.from(ProductDetails(link = link))

        assertEquals(link, item.link)
        assertNull(item.name)
        assertNull(item.imageUrl)
        assertNull(item.currentPrice)
        assertNull(item.currency)
    }
}
