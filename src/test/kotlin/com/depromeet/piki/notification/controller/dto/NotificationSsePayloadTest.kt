package com.depromeet.piki.notification.controller.dto

import com.depromeet.piki.notification.domain.NotificationKind
import com.depromeet.piki.notification.domain.NotificationType
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// SSE payload 의 라우팅 필드 직렬화 contract(#408) — @JsonInclude(NON_NULL) 로 빈 식별자 키가 빠지는지,
// 토너먼트면 모두 실리는지 직렬화 결과로 검증한다. FCM data 키와 같은 이름이라 채널 일관성도 함께 지킨다.
// findAndRegisterModules() 로 JavaTime 모듈을 붙여 LocalDateTime(createdAt) 직렬화를 가능케 한다.
class NotificationSsePayloadTest {
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    private fun payload(
        kind: NotificationKind?,
        tournamentId: Long?,
        tournamentItemId: Long?,
    ) = NotificationSsePayload(
        id = 1L,
        type = NotificationType.ITEM_PARSING_COMPLETED,
        title = "상품 정보가 저장됐어요",
        body = "",
        refId = 11L,
        isRead = false,
        createdAt = LocalDateTime.of(2026, 6, 7, 10, 0),
        kind = kind,
        tournamentId = tournamentId,
        tournamentItemId = tournamentItemId,
    )

    @Test
    fun `위시 payload 는 kind 만 싣고 토너먼트 식별자 키는 직렬화에서 빠진다`() {
        val json = objectMapper.writeValueAsString(payload(NotificationKind.WISH, null, null))

        assertTrue(json.contains("\"kind\":\"WISH\""))
        assertFalse(json.contains("tournamentId"))
        assertFalse(json.contains("tournamentItemId"))
    }

    @Test
    fun `토너먼트 payload 는 kind·tournamentId·tournamentItemId 를 모두 직렬화한다`() {
        val json = objectMapper.writeValueAsString(payload(NotificationKind.TOURNAMENT, 99L, 555L))

        assertTrue(json.contains("\"kind\":\"TOURNAMENT\""))
        assertTrue(json.contains("\"tournamentId\":99"))
        assertTrue(json.contains("\"tournamentItemId\":555"))
    }

    @Test
    fun `라우팅 컨텍스트가 없는 알림은 kind 키도 직렬화에서 빠진다`() {
        val json = objectMapper.writeValueAsString(payload(null, null, null))

        assertFalse(json.contains("kind"))
        assertFalse(json.contains("tournamentId"))
        assertFalse(json.contains("tournamentItemId"))
    }
}
