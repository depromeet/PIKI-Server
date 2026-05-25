package com.depromeet.piki.product.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurrencyCodeTest {
    @ParameterizedTest
    @CsvSource(
        "KRW, KRW",
        "krw, KRW",
        "' KRW ', KRW",
        "usd, USD",
        "Jpy, JPY",
    )
    fun `대소문자·공백을 정규화해 ISO 4217 3자리 대문자로 만든다`(
        raw: String,
        expected: String,
    ) {
        assertEquals(expected, CurrencyCode.normalizeOrNull(raw))
    }

    @ParameterizedTest
    @ValueSource(strings = ["원", "$", "￦", "US", "DOLLAR", "12A", "K-W"])
    fun `ISO 4217 3자리 형식이 아니면 null 로 떨어뜨린다`(raw: String) {
        assertNull(CurrencyCode.normalizeOrNull(raw))
    }

    @ParameterizedTest
    @ValueSource(strings = ["ZZZ", "ABC", "QQQ"])
    fun `형식은 맞지만 실제 ISO 4217 코드가 아니면 null 로 떨어뜨린다`(raw: String) {
        assertNull(CurrencyCode.normalizeOrNull(raw))
    }

    @Test
    fun `null·빈·공백은 null 이다`() {
        assertNull(CurrencyCode.normalizeOrNull(null))
        assertNull(CurrencyCode.normalizeOrNull(""))
        assertNull(CurrencyCode.normalizeOrNull("   "))
    }
}
