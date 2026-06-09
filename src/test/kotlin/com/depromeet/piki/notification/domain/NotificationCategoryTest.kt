package com.depromeet.piki.notification.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

class NotificationCategoryTest {
    // type → category 매핑 전수. TOURNAMENT_* 는 활동, ITEM_PARSING_* 는 시스템.
    @ParameterizedTest
    @CsvSource(
        "TOURNAMENT_JOINED, ACTIVITY",
        "TOURNAMENT_ITEM_ADDED, ACTIVITY",
        "TOURNAMENT_STARTED, ACTIVITY",
        "ITEM_PARSING_COMPLETED, SYSTEM",
        "ITEM_PARSING_FAILED, SYSTEM",
    )
    fun `type 별 카테고리 매핑`(
        type: NotificationType,
        expected: NotificationCategory,
    ) {
        assertEquals(expected, NotificationCategory.of(type))
    }

    @Test
    fun `모든 NotificationType 은 카테고리로 분류된다`() {
        // of 가 when 전수라 누락 시 컴파일이 깨지지만, 분류 자체가 비어있지 않은지도 런타임으로 확인한다.
        NotificationType.entries.forEach { NotificationCategory.of(it) }
    }

    @Test
    fun `typesOf 는 그 카테고리에 속한 type 만 돌려준다`() {
        assertEquals(
            listOf(NotificationType.TOURNAMENT_JOINED, NotificationType.TOURNAMENT_ITEM_ADDED, NotificationType.TOURNAMENT_STARTED),
            NotificationCategory.typesOf(NotificationCategory.ACTIVITY),
        )
        assertEquals(
            listOf(NotificationType.ITEM_PARSING_COMPLETED, NotificationType.ITEM_PARSING_FAILED),
            NotificationCategory.typesOf(NotificationCategory.SYSTEM),
        )
    }

    @Test
    fun `두 카테고리의 type 집합은 전체를 빠짐없이 나눈다`() {
        val all = NotificationCategory.entries.flatMap { NotificationCategory.typesOf(it) }
        assertEquals(NotificationType.entries.toSet(), all.toSet())
        assertEquals(NotificationType.entries.size, all.size) // 중복 없이 분할(partition)
    }
}
