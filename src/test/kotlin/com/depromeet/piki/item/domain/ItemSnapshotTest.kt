package com.depromeet.piki.item.domain

import com.depromeet.piki.product.service.ProductSnapshot
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ItemSnapshotTest {
    @Test
    fun `추출 필드가 모두 채워진 READY 스냅샷을 생성한다`() {
        val snapshot = ItemSnapshot(
            itemId = 1L,
            name = "나이키 에어포스",
            imageUrl = "https://img.example.com/a.png",
            currentPrice = 99_000,
            currency = "KRW",
            status = ItemStatus.READY,
            extractedAt = LocalDateTime.of(2026, 6, 3, 12, 0),
        )
        assertEquals(1L, snapshot.itemId)
        assertEquals("나이키 에어포스", snapshot.name)
        assertEquals(99_000, snapshot.currentPrice)
        assertEquals(ItemStatus.READY, snapshot.status)
    }

    @Test
    fun `추출 전 스냅샷은 status 기본값 PROCESSING 이고 추출 필드가 비어 있어도 생성된다`() {
        val snapshot = ItemSnapshot(itemId = 1L)
        assertEquals(ItemStatus.PROCESSING, snapshot.status)
        assertNull(snapshot.name)
        assertNull(snapshot.currentPrice)
        assertNull(snapshot.imageUrl)
        assertNull(snapshot.currency)
        assertNull(snapshot.extractedAt)
    }

    @Test
    fun `currentPrice 가 음수면 생성에 실패한다`() {
        assertFailsWith<IllegalArgumentException> {
            ItemSnapshot(itemId = 1L, currentPrice = -1)
        }
    }

    @Test
    fun `name 이 512자를 초과하면 생성에 실패한다`() {
        assertFailsWith<IllegalArgumentException> {
            ItemSnapshot(itemId = 1L, name = "가".repeat(513))
        }
    }

    @Test
    fun `imageUrl 이 2048자를 초과하면 생성에 실패한다`() {
        assertFailsWith<IllegalArgumentException> {
            ItemSnapshot(itemId = 1L, imageUrl = "h".repeat(2049))
        }
    }

    @Test
    fun `currency 가 8자를 초과하면 생성에 실패한다`() {
        assertFailsWith<IllegalArgumentException> {
            ItemSnapshot(itemId = 1L, currency = "123456789")
        }
    }

    @Test
    fun `경계값 — name 512자·currency 8자·currentPrice 0 은 허용된다`() {
        val snapshot = ItemSnapshot(
            itemId = 1L,
            name = "가".repeat(512),
            currency = "12345678",
            currentPrice = 0,
        )
        assertEquals(512, snapshot.name?.length)
        assertEquals(8, snapshot.currency?.length)
        assertEquals(0, snapshot.currentPrice)
    }

    // --- 전이 (2단계: item 평행 추적) ---

    @Test
    fun `PROCESSING 스냅샷을 markReady 하면 추출 결과로 채워지고 READY 와 extractedAt 이 설정된다`() {
        val snapshot = ItemSnapshot(itemId = 1L)
        val extractedAt = LocalDateTime.of(2026, 6, 4, 10, 0)
        snapshot.markReady(
            ProductSnapshot(name = "나이키", imageUrl = "https://img.example.com/a.png", currentPrice = 99_000, currency = "KRW"),
            extractedAt,
        )
        assertEquals(ItemStatus.READY, snapshot.status)
        assertEquals("나이키", snapshot.name)
        assertEquals(99_000, snapshot.currentPrice)
        assertEquals(extractedAt, snapshot.extractedAt)
    }

    @Test
    fun `markReady 시 추출 결과에 name 이 없으면 READY 불변식 위반으로 실패한다`() {
        val snapshot = ItemSnapshot(itemId = 1L)
        assertFailsWith<IllegalArgumentException> {
            snapshot.markReady(ProductSnapshot(currentPrice = 1_000), LocalDateTime.of(2026, 6, 4, 10, 0))
        }
    }

    @Test
    fun `PROCESSING 스냅샷을 markFailed 하면 FAILED 가 된다`() {
        val snapshot = ItemSnapshot(itemId = 1L)
        snapshot.markFailed()
        assertEquals(ItemStatus.FAILED, snapshot.status)
    }

    @Test
    fun `FAILED 스냅샷을 recover 하면 채워지고 READY 와 extractedAt 이 설정된다`() {
        val snapshot = ItemSnapshot(itemId = 1L)
        snapshot.markFailed()
        val at = LocalDateTime.of(2026, 6, 4, 11, 0)
        snapshot.recover(name = "수동 보정", currentPrice = 5_000, imageUrl = null, currency = "KRW", extractedAt = at)
        assertEquals(ItemStatus.READY, snapshot.status)
        assertEquals("수동 보정", snapshot.name)
        assertEquals(at, snapshot.extractedAt)
    }

    @Test
    fun `PROCESSING 이 아닌 스냅샷을 markReady 하면 IllegalStateException`() {
        val snapshot = ItemSnapshot(itemId = 1L)
        snapshot.markFailed()
        assertFailsWith<IllegalStateException> {
            snapshot.markReady(ProductSnapshot(name = "x"), LocalDateTime.of(2026, 6, 4, 10, 0))
        }
    }

    @Test
    fun `FAILED 가 아닌 스냅샷을 recover 하면 IllegalStateException`() {
        val snapshot = ItemSnapshot(itemId = 1L)
        assertFailsWith<IllegalStateException> {
            snapshot.recover(name = "x", currentPrice = null, imageUrl = null, currency = null, extractedAt = LocalDateTime.of(2026, 6, 4, 10, 0))
        }
    }
}
