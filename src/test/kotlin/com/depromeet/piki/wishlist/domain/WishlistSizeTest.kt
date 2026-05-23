package com.depromeet.piki.wishlist.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

class WishlistSizeTest {
    @Test
    fun `size 가 null 이면 기본값으로 보정된다`() {
        assertEquals(WishlistSize.DEFAULT, WishlistSize.of(null).value)
    }

    @ParameterizedTest
    @CsvSource(
        "0, 1",
        "-10, 1",
        "1, 1",
        "20, 20",
        "50, 50",
        "51, 50",
        "1000, 50",
    )
    fun `size 는 1~50 범위로 보정된다`(
        raw: Int,
        expected: Int,
    ) {
        assertEquals(expected, WishlistSize.of(raw).value)
    }
}
