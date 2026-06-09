package com.depromeet.piki.auth.infrastructure.oauth.apple

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

class AppleNotificationEventTypeTest {
    @ParameterizedTest
    @CsvSource(
        "account-delete, ACCOUNT_DELETE",
        "consent-revoked, CONSENT_REVOKED",
        "email-disabled, EMAIL_DISABLED",
        "email-enabled, EMAIL_ENABLED",
    )
    fun `Apple 원문 타입을 enum 으로 매핑한다`(
        raw: String,
        expected: AppleNotificationEventType,
    ) {
        assertEquals(expected, AppleNotificationEventType.from(raw))
    }

    @Test
    fun `대소문자와 앞뒤 공백이 섞여도 정규화해 매핑한다`() {
        assertEquals(AppleNotificationEventType.ACCOUNT_DELETE, AppleNotificationEventType.from("  Account-Delete "))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "unknown-type", "delete", "consent_revoked"])
    fun `미상 타입은 UNKNOWN 으로 흡수한다`(raw: String) {
        assertEquals(AppleNotificationEventType.UNKNOWN, AppleNotificationEventType.from(raw))
    }

    @Test
    fun `null 은 UNKNOWN 으로 흡수한다`() {
        assertEquals(AppleNotificationEventType.UNKNOWN, AppleNotificationEventType.from(null))
    }
}
