package com.depromeet.piki.auth.web

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals

class ClientTypeTest {
    @ParameterizedTest
    @CsvSource("web, WEB", "WEB, WEB", "Web, WEB", "' web ', WEB")
    fun `web 값은 대소문자·공백 무시하고 WEB 으로 매핑된다`(
        raw: String,
        expected: ClientType,
    ) {
        assertEquals(expected, ClientType.from(raw))
    }

    @ParameterizedTest
    @ValueSource(strings = ["app", "APP", "ios", "android", "", "  "])
    fun `app·미상 값은 APP 으로 매핑된다`(raw: String) {
        assertEquals(ClientType.APP, ClientType.from(raw))
    }

    @ParameterizedTest
    @NullSource
    fun `헤더 누락(null)은 APP 으로 매핑된다 (graceful default)`(raw: String?) {
        assertEquals(ClientType.APP, ClientType.from(raw))
    }
}
