package com.depromeet.piki.notification.service

import com.depromeet.piki.notification.domain.NotificationType
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.Test
import kotlin.test.assertTrue

class InMemoryNotificationTemplateProviderTest {
    private val provider = InMemoryNotificationTemplateProvider()

    // enum 에 새 타입을 추가하고 시드를 빠뜨리면 이 테스트가 깨져 누락을 컴파일/실행 시점에 드러낸다.
    @ParameterizedTest
    @EnumSource(NotificationType::class)
    fun `모든 NotificationType 에 비어 있지 않은 title 템플릿이 등록되어 있다`(type: NotificationType) {
        val template = provider.find(type)
        assertTrue(template.title.isNotBlank())
    }

    @Test
    fun `TOURNAMENT_ITEM_ADDED 템플릿은 actorName 플레이스홀더를 담는다`() {
        val template = provider.find(NotificationType.TOURNAMENT_ITEM_ADDED)
        assertTrue(template.title.contains("\${actorName}"))
    }

    @Test
    fun `변수가 없는 ITEM_PARSING_COMPLETED 템플릿은 플레이스홀더가 없다`() {
        val template = provider.find(NotificationType.ITEM_PARSING_COMPLETED)
        assertTrue(!template.title.contains("\${"))
    }
}
