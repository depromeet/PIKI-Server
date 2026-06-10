package com.depromeet.piki.notification.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NotificationCursorTest {
    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    fun `빈 값이면 첫 페이지(null)`(raw: String) {
        assertNull(NotificationCursor.parse(raw))
    }

    @Test
    fun `null 이면 첫 페이지(null)`() {
        assertNull(NotificationCursor.parse(null))
    }

    @Test
    fun `숫자면 그 id 를 커서로 파싱한다 (공백 trim)`() {
        assertEquals(1024L, NotificationCursor.parse(" 1024 ")?.lastNotificationId)
    }

    @ParameterizedTest
    @ValueSource(strings = ["abc", "12a", "1.5", "-"])
    fun `숫자로 변환 불가하면 invalidCursor(400)`(raw: String) {
        assertFailsWith<NotificationException> { NotificationCursor.parse(raw) }
    }
}
