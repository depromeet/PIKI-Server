package com.depromeet.piki.auth.infrastructure.oauth

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OAuthProviderTest {
    @ParameterizedTest
    @CsvSource("kakao, KAKAO", "GOOGLE, GOOGLE", "Apple, APPLE", "' kakao ', KAKAO")
    fun `from 은 대소문자·공백 무시하고 enum 으로 매핑한다`(
        raw: String,
        expected: OAuthProvider,
    ) {
        assertEquals(expected, OAuthProvider.from(raw))
    }

    @ParameterizedTest
    @ValueSource(strings = ["naver", "foo", ""])
    fun `정의되지 않은 provider 는 OAuthException 으로 거절된다`(raw: String) {
        assertFailsWith<OAuthException> { OAuthProvider.from(raw) }
    }
}
