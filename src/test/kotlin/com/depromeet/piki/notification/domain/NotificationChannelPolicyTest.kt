package com.depromeet.piki.notification.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationChannelPolicyTest {
    // sync 성 알림(출전 목록 라이브 갱신)은 SSE 만 — OS 트레이 푸시는 노이즈라 보내지 않는다.
    @ParameterizedTest
    @CsvSource("TOURNAMENT_ITEM_ADDED", "TOURNAMENT_ITEM_DELETED")
    fun `sync 성 알림은 SSE 만으로 보낸다`(type: NotificationType) {
        assertEquals(setOf(ChannelKind.SSE), NotificationChannelPolicy.kindsOf(type))
    }

    // alert 성 알림(남의 행동·내 작업 결과·공지)은 앱이 닫혀 있어도 알려야 하므로 SSE+PUSH 둘 다.
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
    fun `alert 성 알림은 SSE 와 PUSH 둘 다로 보낸다`(type: NotificationType) {
        assertEquals(setOf(ChannelKind.SSE, ChannelKind.PUSH), NotificationChannelPolicy.kindsOf(type))
    }

    @Test
    fun `모든 NotificationType 은 채널이 비어있지 않게 분류된다`() {
        // kindsOf 가 when 전수라 누락 시 컴파일이 깨지지만, 분류가 빈 집합이 아닌지도 런타임으로 확인한다.
        NotificationType.entries.forEach { assertTrue(NotificationChannelPolicy.kindsOf(it).isNotEmpty()) }
    }

    @Test
    fun `SSE 는 모든 타입에 포함된다 - 인앱 실시간은 항상이고 PUSH 만 타입별로 갈린다`() {
        NotificationType.entries.forEach { assertTrue(ChannelKind.SSE in NotificationChannelPolicy.kindsOf(it)) }
    }
}
