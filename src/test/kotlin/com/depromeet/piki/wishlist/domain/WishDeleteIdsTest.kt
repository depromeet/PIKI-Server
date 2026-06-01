package com.depromeet.piki.wishlist.domain

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WishDeleteIdsTest {
    @Test
    fun `정상 id 목록은 그대로 보유한다`() {
        val ids = WishDeleteIds.of(listOf(1L, 2L, 3L))
        assertEquals(listOf(1L, 2L, 3L), ids.values)
    }

    @Test
    fun `중복 id 는 distinct 로 정규화된다`() {
        val ids = WishDeleteIds.of(listOf(1L, 1L, 2L, 2L, 2L))
        assertEquals(listOf(1L, 2L), ids.values)
    }

    @Test
    fun `빈 목록이면 WishException(400) 을 던진다`() {
        val e = assertFailsWith<WishException> { WishDeleteIds.of(emptyList()) }
        assertEquals(HttpStatus.BAD_REQUEST, e.httpStatus)
    }

    @Test
    fun `정확히 100개면 허용된다`() {
        val ids = WishDeleteIds.of((1L..100L).toList())
        assertEquals(100, ids.values.size)
    }

    @Test
    fun `100개를 초과하면 WishException(400) 을 던진다`() {
        val e = assertFailsWith<WishException> { WishDeleteIds.of((1L..101L).toList()) }
        assertEquals(HttpStatus.BAD_REQUEST, e.httpStatus)
    }

    @Test
    fun `중복을 제거하면 100개 이하가 되는 입력은 허용된다`() {
        // 정규화(distinct)가 개수 검증보다 먼저라, raw 가 101개여도 distinct 후 100개면 통과한다.
        val raw = (1L..100L).toList() + 100L // 101개지만 distinct 후 100개
        val ids = WishDeleteIds.of(raw)
        assertEquals(100, ids.values.size)
    }
}
