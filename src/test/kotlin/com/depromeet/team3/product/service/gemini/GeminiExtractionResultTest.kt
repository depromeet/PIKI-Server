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
            result.toProduct(link)
        }
    }

    @Test
    fun `name 이 비어 있어도 Product 로 변환되며 name 은 null 로 정규화된다`() {
        val result =
            GeminiExtractionResult(
                isProductPage = true,
                name = "   ",
            )

        val product = result.toProduct(link)

        assertNull(product.name)
    }

    @Test
    fun `일부 필드만 있어도 Product 로 변환된다`() {
        val result =
            GeminiExtractionResult(
                isProductPage = true,
                name = "테스트 상품",
                regularPrice = 10_000,
            )

        val product = result.toProduct(link)

        assertEquals("테스트 상품", product.name)
        assertEquals(10_000, product.regularPrice)
        assertNull(product.discountedPrice)
        assertNull(product.imageUrl)
    }

    @Test
    fun `전 필드가 채워진 경우 그대로 Product 에 매핑되고 discountRate 는 서버에서 계산된다`() {
        val result =
            GeminiExtractionResult(
                isProductPage = true,
                name = "나이키 에어포스",
                regularPrice = 139_000,
                discountedPrice = 99_000,
                currency = "KRW",
                imageUrl = "https://cdn.example.com/p/42.jpg",
            )

        val product = result.toProduct(link)

        assertEquals("나이키 에어포스", product.name)
        assertEquals(139_000, product.regularPrice)
        assertEquals(99_000, product.discountedPrice)
        assertEquals(28, product.discountRate)
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

        val product = result.toProduct(link)

        assertNull(product.imageUrl)
    }

    @Test
    fun `regularPrice 또는 discountedPrice 가 없으면 discountRate 는 null 이다`() {
        val onlyRegular =
            GeminiExtractionResult(
                isProductPage = true,
                name = "상품",
                regularPrice = 10_000,
            )
        assertNull(onlyRegular.toProduct(link).discountRate)

        val onlyDiscounted =
            GeminiExtractionResult(
                isProductPage = true,
                name = "상품",
                discountedPrice = 9_000,
            )
        assertNull(onlyDiscounted.toProduct(link).discountRate)
    }

    @Test
    fun `할인가가 원가 이상이면 discountRate 는 null 이다`() {
        val noDiscount =
            GeminiExtractionResult(
                isProductPage = true,
                name = "상품",
                regularPrice = 10_000,
                discountedPrice = 10_000,
            )

        assertNull(noDiscount.toProduct(link).discountRate)
    }
}
