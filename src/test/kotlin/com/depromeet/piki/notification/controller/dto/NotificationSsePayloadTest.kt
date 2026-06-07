package com.depromeet.piki.notification.controller.dto

import com.depromeet.piki.notification.domain.NotificationType
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 알림 종류별 SSE payload(#408)의 직렬화 셰입 contract — sealed 로 나뉜 각 payload 가 자기 식별자만 직렬화하고
// 없는 키는 아예 안 나오는지 검증한다. FCM data 키와 같은 이름이라 채널 일관성도 함께 지킨다.
// findAndRegisterModules() 로 JavaTime 모듈을 붙여 LocalDateTime(createdAt) 직렬화를 가능케 한다.
class NotificationSsePayloadTest {
    private val objectMapper = ObjectMapper().findAndRegisterModules()
    private val createdAt = LocalDateTime.of(2026, 6, 7, 10, 0)

    @Test
    fun `위시 파싱 payload 는 kind=WISH 만 싣고 토너먼트 식별자 키는 없다`() {
        val payload =
            NotificationSsePayload.WishParsing(
                id = 1L, type = NotificationType.ITEM_PARSING_COMPLETED,
                title = "상품 정보가 저장됐어요", body = "", refId = 11L, isRead = false, createdAt = createdAt,
            )

        val json = objectMapper.writeValueAsString(payload)

        assertTrue(json.contains("\"kind\":\"WISH\""))
        assertTrue(json.contains("\"refId\":11"))
        assertFalse(json.contains("tournamentId"))
        assertFalse(json.contains("tournamentItemId"))
    }

    @Test
    fun `토너먼트 파싱 payload 는 kind·tournamentId·tournamentItemId 를 모두 직렬화한다`() {
        val payload =
            NotificationSsePayload.TournamentParsing(
                id = 1L, type = NotificationType.ITEM_PARSING_COMPLETED,
                title = "상품 정보가 저장됐어요", body = "", refId = 11L, isRead = false, createdAt = createdAt,
                tournamentId = 99L, tournamentItemId = 555L,
            )

        val json = objectMapper.writeValueAsString(payload)

        assertTrue(json.contains("\"kind\":\"TOURNAMENT\""))
        assertTrue(json.contains("\"tournamentId\":99"))
        assertTrue(json.contains("\"tournamentItemId\":555"))
    }

    @Test
    fun `라우팅 없는 Reference payload 는 kind·토너먼트 식별자 키가 모두 없다`() {
        val payload =
            NotificationSsePayload.Reference(
                id = 1L, type = NotificationType.TOURNAMENT_JOINED,
                title = "홍길동님이 참가했어요", body = "", refId = 42L, isRead = false, createdAt = createdAt,
            )

        val json = objectMapper.writeValueAsString(payload)

        assertFalse(json.contains("kind"))
        assertFalse(json.contains("tournamentId"))
        assertFalse(json.contains("tournamentItemId"))
    }
}
