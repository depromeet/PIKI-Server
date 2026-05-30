package com.depromeet.piki.auth.web

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals

class ClientTypeTest {
    @ParameterizedTest
    @ValueSource(strings = ["app", "APP", "App", " app "])
    fun `app 값만 대소문자·공백 무시하고 APP 으로 매핑된다`(raw: String) {
        assertEquals(ClientType.APP, ClientType.from(raw))
    }

    @ParameterizedTest
    @ValueSource(strings = ["web", "WEB", "ios", "android", "weeb", "", "  "])
    fun `web·미상·빈 값은 모두 WEB 으로 매핑된다 (secure by default)`(raw: String) {
        assertEquals(ClientType.WEB, ClientType.from(raw))
    }

    @ParameterizedTest
    @NullSource
    fun `헤더 누락(null)은 WEB 으로 매핑된다 (secure by default)`(raw: String?) {
        assertEquals(ClientType.WEB, ClientType.from(raw))
    }
}
