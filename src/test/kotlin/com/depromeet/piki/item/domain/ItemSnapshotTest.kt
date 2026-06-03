package com.depromeet.piki.item.domain

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ItemSnapshotTest {
    @Test
    fun `추출 필드가 모두 채워진 READY 버전을 생성한다`() {
        val snapshot = ItemSnapshot(
            itemId = 1L,
            version = 2,
            name = "나이키 에어포스",
            imageUrl = "https://img.example.com/a.png",
            currentPrice = 99_000,
            currency = "KRW",
            status = ItemStatus.READY,
            extractedAt = LocalDateTime.of(2026, 6, 3, 12, 0),
        )
        assertEquals(1L, snapshot.itemId)
        assertEquals(2, snapshot.version)
        assertEquals("나이키 에어포스", snapshot.name)
        assertEquals(99_000, snapshot.currentPrice)
        assertEquals(ItemStatus.READY, snapshot.status)
    }

    @Test
    fun `추출 전 버전은 status 기본값 PROCESSING 이고 추출 필드가 비어 있어도 생성된다`() {
        val snapshot = ItemSnapshot(itemId = 1L, version = 1)
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
            ItemSnapshot(itemId = 1L, version = 1, currentPrice = -1)
        }
    }

    @Test
    fun `name 이 512자를 초과하면 생성에 실패한다`() {
        assertFailsWith<IllegalArgumentException> {
            ItemSnapshot(itemId = 1L, version = 1, name = "가".repeat(513))
        }
    }

    @Test
    fun `imageUrl 이 2048자를 초과하면 생성에 실패한다`() {
        assertFailsWith<IllegalArgumentException> {
            ItemSnapshot(itemId = 1L, version = 1, imageUrl = "h".repeat(2049))
        }
    }

    @Test
    fun `currency 가 8자를 초과하면 생성에 실패한다`() {
        assertFailsWith<IllegalArgumentException> {
            ItemSnapshot(itemId = 1L, version = 1, currency = "123456789")
        }
    }

    @Test
    fun `경계값 — name 512자·currency 8자·currentPrice 0 은 허용된다`() {
        val snapshot = ItemSnapshot(
            itemId = 1L,
            version = 1,
            name = "가".repeat(512),
            currency = "12345678",
            currentPrice = 0,
        )
        assertEquals(512, snapshot.name?.length)
        assertEquals(8, snapshot.currency?.length)
        assertEquals(0, snapshot.currentPrice)
    }
}
