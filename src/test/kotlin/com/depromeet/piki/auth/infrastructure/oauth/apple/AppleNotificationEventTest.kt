package com.depromeet.piki.auth.infrastructure.oauth.apple

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppleNotificationEventTest {
    @Test
    fun `events JSON 에서 type 과 sub 를 추출한다`() {
        val json = """{"type":"account-delete","sub":"001234.abcdef","event_time":1700000000000}"""

        val event = AppleNotificationEvent.parse(json)

        assertEquals(AppleNotificationEventType.ACCOUNT_DELETE, event.type)
        assertEquals("001234.abcdef", event.sub)
    }

    @Test
    fun `미지원 type 은 UNKNOWN 으로 파싱한다`() {
        val json = """{"type":"foo-bar","sub":"001"}"""

        assertEquals(AppleNotificationEventType.UNKNOWN, AppleNotificationEvent.parse(json).type)
    }

    @Test
    fun `sub 가 없으면 null 이다`() {
        val json = """{"type":"consent-revoked","event_time":1}"""

        val event = AppleNotificationEvent.parse(json)

        assertEquals(AppleNotificationEventType.CONSENT_REVOKED, event.type)
        assertNull(event.sub)
    }

    @Test
    fun `sub 가 공백이면 null 로 정규화한다`() {
        val json = """{"type":"account-delete","sub":"   "}"""

        assertNull(AppleNotificationEvent.parse(json).sub)
    }

    @Test
    fun `우리가 보지 않는 필드(email 등)는 무시한다`() {
        val json =
            """{"type":"email-disabled","sub":"001","email":"x@privaterelay.appleid.com","is_private_email":"true"}"""

        val event = AppleNotificationEvent.parse(json)

        assertEquals(AppleNotificationEventType.EMAIL_DISABLED, event.type)
        assertEquals("001", event.sub)
    }
}
