package com.depromeet.piki.notification.handler

import com.depromeet.piki.item.event.ItemParsingCompleted
import com.depromeet.piki.item.event.ItemParsingFailed
import com.depromeet.piki.notification.domain.NotificationType
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.tournament.event.TournamentItemAdded
import com.depromeet.piki.tournament.event.TournamentJoined
import com.depromeet.piki.tournament.event.TournamentStarted
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

// 핸들러가 이제 repo·resolver 를 주입받으므로 무인자 생성이 불가하다. 실제 와이어링된 빈을 autowire 해
// 베이스 클래스의 제네릭 eventType 도출·notificationType·유일성을 검증한다(내부 모킹 없이 실제 빈으로).
class NotificationEventHandlerTest : IntegrationTestSupport() {
    @Autowired private lateinit var itemParsingCompletedHandler: ItemParsingCompletedHandler

    @Autowired private lateinit var itemParsingFailedHandler: ItemParsingFailedHandler

    @Autowired private lateinit var tournamentItemAddedHandler: TournamentItemAddedHandler

    @Autowired private lateinit var tournamentJoinedHandler: TournamentJoinedHandler

    @Autowired private lateinit var tournamentStartedHandler: TournamentStartedHandler

    @Autowired private lateinit var handlers: List<NotificationEventHandler<*>>

    // eventType 은 제네릭 타입 인자 E 에서 GenericTypeResolver 로 자동 도출된다(::class 명시 제거).
    // reflection 기반이라 클래스 계층이 바뀌면 조용히 틀어질 수 있어, 도출 결과를 직접 못 박아 회귀를 잡는다.
    @Test
    fun `각 핸들러의 eventType 이 제네릭 인자에서 올바르게 도출된다`() {
        assertEquals(ItemParsingCompleted::class, itemParsingCompletedHandler.eventType)
        assertEquals(ItemParsingFailed::class, itemParsingFailedHandler.eventType)
        assertEquals(TournamentItemAdded::class, tournamentItemAddedHandler.eventType)
        assertEquals(TournamentJoined::class, tournamentJoinedHandler.eventType)
        assertEquals(TournamentStarted::class, tournamentStartedHandler.eventType)
    }

    @Test
    fun `notificationType 이 생성자로 주입돼 핸들러와 짝이 맞는다`() {
        assertEquals(NotificationType.ITEM_PARSING_COMPLETED, itemParsingCompletedHandler.notificationType)
        assertEquals(NotificationType.ITEM_PARSING_FAILED, itemParsingFailedHandler.notificationType)
        assertEquals(NotificationType.TOURNAMENT_ITEM_ADDED, tournamentItemAddedHandler.notificationType)
        assertEquals(NotificationType.TOURNAMENT_JOINED, tournamentJoinedHandler.notificationType)
        assertEquals(NotificationType.TOURNAMENT_STARTED, tournamentStartedHandler.notificationType)
    }

    // Dispatcher 는 eventType 으로 라우팅하므로 키가 유일해야 한다(중복이면 associateBy 가 조용히 덮어쓴다).
    // 등록된 모든 핸들러 빈을 받아 검사하므로, 새 핸들러가 같은 eventType 으로 끼면 여기서 잡힌다.
    @Test
    fun `모든 핸들러의 eventType 은 서로 겹치지 않는다`() {
        val eventTypes = handlers.map { it.eventType }
        assertEquals(eventTypes.size, eventTypes.toSet().size)
    }
}
