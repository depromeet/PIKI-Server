package com.depromeet.piki.item.domain

import com.depromeet.piki.product.domain.ProductLink
import com.depromeet.piki.product.service.ProductSnapshot
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `name 만 있는 ProductSnapshot 은 나머지 optional 필드를 null 로 보존한다`() {
        val item = Item.from(ProductSnapshot(link = link, name = "최소 상품"))

        assertEquals(link, item.link)
        assertEquals("최소 상품", item.name)
        assertNull(item.imageUrl)
        assertNull(item.currentPrice)
        assertNull(item.currency)
    }

    @Test
    fun `name 없는 snapshot 은 from 으로 READY item 이 될 수 없다`() {
        // READY 불변식 — 이름 없는 상품은 목록·토너먼트에서 쓸 수 없어 READY 로 만들지 않는다.
        assertFailsWith<IllegalArgumentException> { Item.from(ProductSnapshot(link = link, currentPrice = 1_000)) }
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
    fun `recover 로 FAILED item 의 name·currentPrice·imageUrl·currency 를 모두 채우면 READY 로 복구된다`() {
        val item =
            Item(
                link = link,
                name = "원래 이름",
                imageUrl = "https://cdn.example.com/old.jpg",
                currentPrice = 10_000,
                currency = "USD",
                status = ItemStatus.FAILED,
            )

        item.recover(
            name = "새 이름",
            currentPrice = 20_000,
            imageUrl = "https://cdn.example.com/new.jpg",
            currency = "KRW",
        )

        assertEquals(ItemStatus.READY, item.status)
        assertEquals("새 이름", item.name)
        assertEquals(20_000, item.currentPrice)
        assertEquals("https://cdn.example.com/new.jpg", item.imageUrl)
        assertEquals("KRW", item.currency)
    }

    @Test
    fun `recover 에 currency 만 주면 나머지 기존 값은 유지된다`() {
        val item = Item(link = link, name = "원래 이름", currentPrice = 10_000, currency = "USD", status = ItemStatus.FAILED)

        item.recover(currency = "KRW")

        assertEquals("원래 이름", item.name)
        assertEquals(10_000, item.currentPrice)
        assertEquals("KRW", item.currency)
    }

    @Test
    fun `recover 로 currency 를 8자 초과로 주면 불변식 위반으로 거부된다`() {
        val item = Item(link = link, currency = "KRW", status = ItemStatus.FAILED)

        assertFailsWith<IllegalArgumentException> { item.recover(currency = "a".repeat(9)) }
    }

    @Test
    fun `recover 로 imageUrl 을 2048자 초과로 주면 불변식 위반으로 거부된다`() {
        val item = Item(link = link, imageUrl = "https://example.com/img.jpg", status = ItemStatus.FAILED)

        assertFailsWith<IllegalArgumentException> { item.recover(imageUrl = "a".repeat(2049)) }
    }

    @Test
    fun `imageUrl 이 2048자를 초과하면 Item 생성이 거부된다`() {
        assertFailsWith<IllegalArgumentException> {
            Item(link = link, imageUrl = "a".repeat(2049))
        }
    }

    @Test
    fun `recover 에 name 만 주면 기존 currentPrice 는 유지된다`() {
        val item = Item(link = link, name = "원래 이름", currentPrice = 10_000, status = ItemStatus.FAILED)

        item.recover(name = "새 이름", currentPrice = null)

        assertEquals("새 이름", item.name)
        assertEquals(10_000, item.currentPrice)
    }

    @Test
    fun `이름 없는 FAILED item 을 보정값 없이 recover 하면 nameRequiredForReady(400)로 거부된다`() {
        // 실제 FAILED item 은 추출이 안 돼 이름이 비어 있다. 이름 없이 보정하면 쓸 수 없는 상품이 READY 로 새어 들어가므로 400.
        val item = Item.processing(link).apply { markFailed() }

        val ex = assertFailsWith<ItemException> { item.recover(currentPrice = 50_000) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.httpStatus)
        // 거부됐으므로 FAILED 그대로다.
        assertEquals(ItemStatus.FAILED, item.status)
    }

    @Test
    fun `recover 로 currentPrice 를 음수로 주면 불변식 위반으로 거부된다`() {
        val item = Item(link = link, currentPrice = 10_000, status = ItemStatus.FAILED)

        assertFailsWith<IllegalArgumentException> { item.recover(currentPrice = -1) }
    }

    @Test
    fun `recover 로 name 을 512자 초과로 주면 불변식 위반으로 거부된다`() {
        val item = Item(link = link, name = "원래 이름", status = ItemStatus.FAILED)

        assertFailsWith<IllegalArgumentException> { item.recover(name = "가".repeat(513)) }
    }

    @Test
    fun `link 가 null 인 ProductSnapshot(이미지 경로)도 Item 으로 매핑된다`() {
        val snapshot = ProductSnapshot(link = null, name = "나이키 에어포스", currentPrice = 99_000, currency = "KRW")

        val item = Item.from(snapshot)

        assertNull(item.link)
        assertEquals("나이키 에어포스", item.name)
        assertEquals(99_000, item.currentPrice)
        assertEquals("KRW", item.currency)
        assertNull(item.imageUrl)
    }

    @Test
    fun `from 으로 만든 item 은 READY 상태다`() {
        val item = Item.from(ProductSnapshot(link = link, name = "나이키"))

        assertEquals(ItemStatus.READY, item.status)
    }

    @Test
    fun `processing 으로 만든 item 은 PROCESSING 상태이며 추출 필드가 비어 있다`() {
        val item = Item.processing(link)

        assertEquals(ItemStatus.PROCESSING, item.status)
        assertEquals(link, item.link)
        assertNull(item.name)
        assertNull(item.currentPrice)
        assertNull(item.imageUrl)
        assertNull(item.currency)
    }

    @Test
    fun `PROCESSING item 을 markReady 하면 READY 로 전이하며 추출 결과가 채워진다`() {
        val item = Item.processing(link)

        item.markReady(
            ProductSnapshot(
                link = link,
                name = "나이키 에어포스",
                currentPrice = 99_000,
                currency = "KRW",
                imageUrl = "https://cdn.example.com/p/42.jpg",
            ),
        )

        assertEquals(ItemStatus.READY, item.status)
        assertEquals("나이키 에어포스", item.name)
        assertEquals(99_000, item.currentPrice)
        assertEquals("KRW", item.currency)
        assertEquals("https://cdn.example.com/p/42.jpg", item.imageUrl)
    }

    @Test
    fun `markReady 에 이름 없는 snapshot 이면 READY 불변식 위반으로 거부된다`() {
        // 추출 성공이라도 이름을 못 얻으면 READY 부적격. 워커는 이 예외를 받아 FAILED 로 흡수한다.
        val item = Item.processing(link)

        assertFailsWith<IllegalArgumentException> {
            item.markReady(ProductSnapshot(link = link, currentPrice = 99_000))
        }
        // 전이가 거부됐으므로 PROCESSING 그대로다 (워커가 이후 FAILED 로 떨어뜨린다).
        assertEquals(ItemStatus.PROCESSING, item.status)
    }

    @Test
    fun `PROCESSING item 을 markFailed 하면 FAILED 로 전이한다`() {
        val item = Item.processing(link)

        item.markFailed()

        assertEquals(ItemStatus.FAILED, item.status)
    }

    @Test
    fun `이미 READY 인 item 을 다시 markReady 하면 전이 불변식 위반으로 거부된다`() {
        val item = Item.from(ProductSnapshot(link = link, name = "나이키"))

        assertFailsWith<IllegalStateException> { item.markReady(ProductSnapshot(link = link, name = "변경")) }
    }

    @Test
    fun `READY 인 item 을 markFailed 하면 전이 불변식 위반으로 거부된다`() {
        val item = Item.from(ProductSnapshot(link = link, name = "나이키"))

        assertFailsWith<IllegalStateException> { item.markFailed() }
    }

    @Test
    fun `FAILED 인 item 을 markReady 하면 전이 불변식 위반으로 거부된다`() {
        val item = Item.processing(link).apply { markFailed() }

        assertFailsWith<IllegalStateException> { item.markReady(ProductSnapshot(link = link, name = "나이키")) }
    }

    @Test
    fun `isReady 는 READY 일 때만 true 다`() {
        assertTrue(Item.from(ProductSnapshot(link = link, name = "나이키")).isReady())
        assertFalse(Item.processing(link).isReady())
        assertFalse(Item.processing(link).apply { markFailed() }.isReady())
    }

    @Test
    fun `FAILED item 을 recover 로 직접 보정하면 READY 로 복구되며 보정값이 반영된다`() {
        val item = Item.processing(link).apply { markFailed() }

        item.recover(name = "직접 입력한 이름", currentPrice = 50_000)

        assertEquals(ItemStatus.READY, item.status)
        assertEquals("직접 입력한 이름", item.name)
        assertEquals(50_000, item.currentPrice)
    }

    @Test
    fun `READY item 을 recover 하면 등록 완료 상태라 alreadyReady(409)로 거부된다`() {
        val item = Item.from(ProductSnapshot(link = link, name = "나이키"))

        val ex = assertFailsWith<ItemException> { item.recover(name = "수정 시도") }
        assertEquals(HttpStatus.CONFLICT, ex.httpStatus)
        // 거부됐으므로 기존 값·상태는 그대로다.
        assertEquals(ItemStatus.READY, item.status)
        assertEquals("나이키", item.name)
    }

    @Test
    fun `PROCESSING item 을 recover 하면 워커 소관이라 stillProcessing(409)로 거부된다`() {
        // 파싱 중 항목의 status 전이는 백그라운드 워커(markReady/markFailed)가 책임지므로 클라이언트가 끼어들 수 없다.
        val item = Item.processing(link)

        val ex = assertFailsWith<ItemException> { item.recover(name = "끼어든 수정") }
        assertEquals(HttpStatus.CONFLICT, ex.httpStatus)
        assertEquals(ItemStatus.PROCESSING, item.status)
    }
}
