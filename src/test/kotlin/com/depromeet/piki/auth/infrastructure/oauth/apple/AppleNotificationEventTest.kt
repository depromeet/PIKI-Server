package com.depromeet.piki.auth.infrastructure.oauth.apple

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @ParameterizedTest
    @ValueSource(strings = ["account-delete", "consent-revoked"])
    fun `탈퇴·세션종료 이벤트는 sub 가 없으면 예외를 던진다`(type: String) {
        // sub 누락은 비정상 payload — 멱등 무시(200)로 삼키지 않고 형식 오류로 막아 호출자가 401 로 거부하게 한다.
        val json = """{"type":"$type","event_time":1}"""

        assertFailsWith<IllegalArgumentException> { AppleNotificationEvent.parse(json) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["account-delete", "consent-revoked"])
    fun `탈퇴·세션종료 이벤트는 sub 가 공백이면 예외를 던진다`(type: String) {
        val json = """{"type":"$type","sub":"   "}"""

        assertFailsWith<IllegalArgumentException> { AppleNotificationEvent.parse(json) }
    }

    @Test
    fun `email·unknown 이벤트는 sub 가 없어도 허용한다 (로그만 하므로 대상 유저 불필요)`() {
        assertNull(AppleNotificationEvent.parse("""{"type":"email-disabled"}""").sub)
        assertNull(AppleNotificationEvent.parse("""{"type":"email-enabled"}""").sub)
        assertNull(AppleNotificationEvent.parse("""{"type":"foo-bar"}""").sub)
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
