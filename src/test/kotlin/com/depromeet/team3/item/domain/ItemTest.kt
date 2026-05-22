package com.depromeet.team3.item.domain

import com.depromeet.team3.common.domain.Product
import com.depromeet.team3.product.domain.ProductLink
import com.depromeet.team3.product.service.ProductSnapshot
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ItemTest {
    private val link = ProductLink.parse("https://shop.example.com/products/42")

    @Test
    fun `ProductSnapshot 의 전 필드가 Item 으로 매핑된다`() {
        val snapshot =
            ProductSnapshot(
                link = link,
                name = "나이키 에어포스",
                imageUrl = "https://cdn.example.com/p/42.jpg",
                currentPrice = 99_000,
                currency = "KRW",
            )

        val item = Item.from(snapshot)

        assertEquals(link, item.link)
        assertEquals("나이키 에어포스", item.name)
        assertEquals("https://cdn.example.com/p/42.jpg", item.imageUrl)
        assertEquals(99_000, item.currentPrice)
        assertEquals("KRW", item.currency)
    }

    @Test
    fun `link 만 있는 ProductSnapshot 도 Item 으로 매핑되며 나머지는 null 로 보존된다`() {
        val item = Item.from(ProductSnapshot(link = link))

        assertEquals(link, item.link)
        assertNull(item.name)
        assertNull(item.imageUrl)
        assertNull(item.currentPrice)
        assertNull(item.currency)
    }

    @Test
    fun `currentPrice 가 음수면 Item 생성이 거부된다`() {
        assertFailsWith<IllegalArgumentException> {
            Item(link = link, currentPrice = -1)
        }
    }

    @Test
    fun `name 이 512자를 초과하면 Item 생성이 거부된다`() {
        assertFailsWith<IllegalArgumentException> {
            Item(link = link, name = "가".repeat(513))
        }
    }

    @Test
    fun `update 로 name·currentPrice·imageUrl·currency 를 모두 교체한다`() {
        val item =
            Item(
                link = link,
                name = "원래 이름",
                imageUrl = "https://cdn.example.com/old.jpg",
                currentPrice = 10_000,
                currency = "USD",
            )

        item.update(
            name = "새 이름",
            currentPrice = 20_000,
            imageUrl = "https://cdn.example.com/new.jpg",
            currency = "KRW",
        )

        assertEquals("새 이름", item.name)
        assertEquals(20_000, item.currentPrice)
        assertEquals("https://cdn.example.com/new.jpg", item.imageUrl)
        assertEquals("KRW", item.currency)
    }

    @Test
    fun `update 에 currency 만 주면 나머지는 유지된다`() {
        val item = Item(link = link, name = "원래 이름", currentPrice = 10_000, currency = "USD")

        item.update(currency = "KRW")

        assertEquals("원래 이름", item.name)
        assertEquals(10_000, item.currentPrice)
        assertEquals("KRW", item.currency)
    }

    @Test
    fun `currency 를 8자 초과로 update 하면 불변식 위반으로 거부된다`() {
        val item = Item(link = link, currency = "KRW")

        assertFailsWith<IllegalArgumentException> { item.update(currency = "a".repeat(9)) }
    }

    @Test
    fun `imageUrl 을 2048자 초과로 update 하면 불변식 위반으로 거부된다`() {
        val item = Item(link = link, imageUrl = "https://example.com/img.jpg")

        assertFailsWith<IllegalArgumentException> { item.update(imageUrl = "a".repeat(2049)) }
    }

    @Test
    fun `imageUrl 이 2048자를 초과하면 Item 생성이 거부된다`() {
        assertFailsWith<IllegalArgumentException> {
            Item(link = link, imageUrl = "a".repeat(2049))
        }
    }

    @Test
    fun `update 에 name 만 주면 currentPrice 는 유지된다`() {
        val item = Item(link = link, name = "원래 이름", currentPrice = 10_000)

        item.update(name = "새 이름", currentPrice = null)

        assertEquals("새 이름", item.name)
        assertEquals(10_000, item.currentPrice)
    }

    @Test
    fun `update 인자가 모두 null 이면 기존 값이 유지된다`() {
        val item = Item(link = link, name = "원래 이름", currentPrice = 10_000)

        item.update(name = null, currentPrice = null)

        assertEquals("원래 이름", item.name)
        assertEquals(10_000, item.currentPrice)
    }

    @Test
    fun `currentPrice 를 음수로 update 하면 불변식 위반으로 거부된다`() {
        val item = Item(link = link, currentPrice = 10_000)

        assertFailsWith<IllegalArgumentException> { item.update(currentPrice = -1) }
    }

    @Test
    fun `name 을 512자 초과로 update 하면 불변식 위반으로 거부된다`() {
        val item = Item(link = link, name = "원래 이름")

        assertFailsWith<IllegalArgumentException> { item.update(name = "가".repeat(513)) }
    }

    @Test
    fun `fromOcr 은 link·image 없이 name·price·currency 를 매핑하고 category 는 버린다`() {
        val product = Product(name = "나이키 에어포스", price = 99_000, category = "신발", currency = "KRW")

        val item = Item.fromOcr(product)

        assertNull(item.link)
        assertEquals("나이키 에어포스", item.name)
        assertEquals(99_000, item.currentPrice)
        assertEquals("KRW", item.currency)
        assertNull(item.imageUrl)
    }

    @Test
    fun `link 없이도 Item 이 생성된다 (OCR 경로)`() {
        val item = Item(link = null, name = "상품")

        assertNull(item.link)
        assertEquals("상품", item.name)
    }
}
