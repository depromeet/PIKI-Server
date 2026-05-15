package com.depromeet.team3.product.service.gemini

import com.depromeet.team3.product.domain.ProductLink
import com.depromeet.team3.product.service.ProductExtractionException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GeminiExtractionResultTest {
    private val link = ProductLink.parse("https://shop.example.com/products/42")

    @Test
    fun `isProductPage 가 false 이면 notProductPage 예외를 던진다`() {
        val result =
            GeminiExtractionResult(
                isProductPage = false,
                name = null,
            )

        assertFailsWith<ProductExtractionException> {
            result.toProductDetails(link)
        }
    }

    @Test
    fun `name 이 비어 있어도 ProductDetails 으로 변환되며 name 은 null 로 정규화된다`() {
        val result =
            GeminiExtractionResult(
                isProductPage = true,
                name = "   ",
            )

        val product = result.toProductDetails(link)

        assertNull(product.name)
    }

    @Test
    fun `일부 필드만 있어도 ProductDetails 으로 변환된다`() {
        val result =
            GeminiExtractionResult(
                isProductPage = true,
                name = "테스트 상품",
                currentPrice = 10_000,
            )

        val product = result.toProductDetails(link)

        assertEquals("테스트 상품", product.name)
        assertEquals(10_000, product.currentPrice)
        assertNull(product.imageUrl)
    }

    @Test
    fun `전 필드가 채워진 경우 그대로 ProductDetails 에 매핑된다`() {
        val result =
            GeminiExtractionResult(
                isProductPage = true,
                name = "나이키 에어포스",
                currentPrice = 99_000,
                currency = "KRW",
                imageUrl = "https://cdn.example.com/p/42.jpg",
            )

        val product = result.toProductDetails(link)

        assertEquals("나이키 에어포스", product.name)
        assertEquals(99_000, product.currentPrice)
        assertEquals("KRW", product.currency)
        assertEquals("https://cdn.example.com/p/42.jpg", product.imageUrl)
        assertEquals(link, product.link)
    }

    @Test
    fun `비어 있는 문자열 필드는 null 로 정규화된다`() {
        val result =
            GeminiExtractionResult(
                isProductPage = true,
                name = "테스트",
                imageUrl = "",
            )

        val product = result.toProductDetails(link)

        assertNull(product.imageUrl)
    }
}
