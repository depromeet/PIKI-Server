package com.depromeet.piki.notification.handler

import com.depromeet.piki.item.event.ItemParsingCompleted
import com.depromeet.piki.item.event.ItemParsingFailed
import com.depromeet.piki.notification.domain.NotificationType
import com.depromeet.piki.tournament.event.TournamentItemAdded
import com.depromeet.piki.tournament.event.TournamentJoined
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationEventHandlerTest {
    // eventType 은 제네릭 타입 인자 E 에서 ResolvableType 으로 자동 도출된다(::class 명시 제거).
    // reflection 기반이라 클래스 계층이 바뀌면 조용히 틀어질 수 있어, 도출 결과를 직접 못 박아 회귀를 잡는다.
    @Test
    fun `각 핸들러의 eventType 이 제네릭 인자에서 올바르게 도출된다`() {
        assertEquals(ItemParsingCompleted::class, ItemParsingCompletedHandler().eventType)
        assertEquals(ItemParsingFailed::class, ItemParsingFailedHandler().eventType)
        assertEquals(TournamentItemAdded::class, TournamentItemAddedHandler().eventType)
        assertEquals(TournamentJoined::class, TournamentJoinedHandler().eventType)
    }

    @Test
    fun `notificationType 이 생성자로 주입돼 핸들러와 짝이 맞는다`() {
        assertEquals(NotificationType.ITEM_PARSING_COMPLETED, ItemParsingCompletedHandler().notificationType)
        assertEquals(NotificationType.ITEM_PARSING_FAILED, ItemParsingFailedHandler().notificationType)
        assertEquals(NotificationType.TOURNAMENT_ITEM_ADDED, TournamentItemAddedHandler().notificationType)
        assertEquals(NotificationType.TOURNAMENT_JOINED, TournamentJoinedHandler().notificationType)
    }

    // Dispatcher 는 eventType 으로 라우팅하므로 키가 유일해야 한다(중복이면 associateBy 가 조용히 덮어쓴다).
    @Test
    fun `모든 핸들러의 eventType 은 서로 겹치지 않는다`() {
        val eventTypes =
            listOf(
                ItemParsingCompletedHandler().eventType,
                ItemParsingFailedHandler().eventType,
                TournamentItemAddedHandler().eventType,
                TournamentJoinedHandler().eventType,
            )
        assertEquals(eventTypes.size, eventTypes.toSet().size)
    }
}
