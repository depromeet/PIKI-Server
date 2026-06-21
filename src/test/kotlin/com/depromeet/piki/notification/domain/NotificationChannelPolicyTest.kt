package com.depromeet.piki.notification.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationChannelPolicyTest {
    // 아이템 추가·삭제 알림은 인앱 SSE 로 즉시 반영되므로 OS(FCM) 푸시 대상이 아니다.
    @ParameterizedTest
    @CsvSource("TOURNAMENT_ITEM_ADDED", "TOURNAMENT_ITEM_DELETED")
    fun `아이템 추가·삭제 알림은 FCM 푸시 대상이 아니다`(type: NotificationType) {
        assertFalse(NotificationChannelPolicy.pushable(type))
    }

    // 그 외 알림(남의 행동·내 작업 결과·공지)은 앱이 닫혀 있어도 알려야 하므로 FCM 푸시 대상이다.
    @ParameterizedTest
    @CsvSource(
        "TOURNAMENT_JOINED",
        "TOURNAMENT_STARTED",
        "TOURNAMENT_PLAYED_FROM_LINK",
        "TOURNAMENT_COMPLETED",
        "TOURNAMENT_RESULT_READY",
        "ITEM_PARSING_COMPLETED",
        "ITEM_PARSING_FAILED",
        "ANNOUNCEMENT",
    )
    fun `그 외 알림은 모두 FCM 푸시 대상이다`(type: NotificationType) {
        assertTrue(NotificationChannelPolicy.pushable(type))
    }

    @Test
    fun `푸시 비대상은 아이템 추가·삭제 둘 뿐이다 - 나머지 전 타입은 푸시 대상`() {
        // pushable 이 when 전수라 분류 누락은 컴파일에서 깨지지만, "SSE only 가 이 둘로 한정"이라는 의도도 못 박는다.
        val nonPushable = NotificationType.entries.filterNot { NotificationChannelPolicy.pushable(it) }.toSet()
        assertEquals(setOf(NotificationType.TOURNAMENT_ITEM_ADDED, NotificationType.TOURNAMENT_ITEM_DELETED), nonPushable)
    }
}
