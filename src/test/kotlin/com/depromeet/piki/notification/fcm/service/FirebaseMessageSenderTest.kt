package com.depromeet.piki.notification.fcm.service

import com.depromeet.piki.notification.domain.Notification
import com.depromeet.piki.notification.domain.NotificationRouting
import com.depromeet.piki.notification.domain.NotificationType
import com.google.firebase.messaging.MessagingErrorCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 죽은 토큰 판정 분기 망라(#245). FirebaseMessaging 응답과 무관한 순수 정책이라 FirebaseApp 없이 단위로 검증한다.
// (멀티캐스트 chunk 루프·발송 실패 스킵은 FirebaseMessaging 호출에 강결합이라 여기서 다루지 않는다 —
//  채널 레벨 fan-out·정리는 PushNotificationChannelIntegrationTest 가 stub 으로 덮는다.)
class FirebaseMessageSenderTest {
    @Test
    fun `UNREGISTERED 만 정리 대상이다`() {
        assertTrue(FirebaseMessageSender.isStaleToken(MessagingErrorCode.UNREGISTERED))
    }

    // INVALID_ARGUMENT 는 토큰이 아닌 요청·메시지 문제에도 와서 보존한다(정상 토큰 대량 삭제 방지).
    // 그 외 일시 오류도 재시도 위해 보존.
    @ParameterizedTest
    @EnumSource(
        value = MessagingErrorCode::class,
        names = ["UNREGISTERED"],
        mode = EnumSource.Mode.EXCLUDE,
    )
    fun `UNREGISTERED 외 에러코드는 보존한다`(code: MessagingErrorCode) {
        assertFalse(FirebaseMessageSender.isStaleToken(code))
    }

    @Test
    fun `에러코드가 없으면 보존한다`() {
        assertFalse(FirebaseMessageSender.isStaleToken(null))
    }

    // buildDataPayload 는 FCM data(키→값) 구성 정책 — 라우팅 컨텍스트 유무에 따른 키 셋 분기를 단위로 망라한다(#408).
    @Test
    fun `라우팅 없는 알림은 type·refId 만 data 에 싣는다`() {
        val notification = Notification(UUID.randomUUID(), NotificationType.TOURNAMENT_JOINED, "제목", "본문", 7L)

        val data = FirebaseMessageSender.buildDataPayload(notification)

        assertEquals(mapOf("type" to "TOURNAMENT_JOINED", "refId" to "7"), data)
    }

    @Test
    fun `위시 파싱 알림은 kind=WISH 만 더 싣고 토너먼트 키는 생략한다`() {
        val notification =
            Notification(UUID.randomUUID(), NotificationType.ITEM_PARSING_COMPLETED, "제목", "본문", 11L, NotificationRouting.Wish)

        val data = FirebaseMessageSender.buildDataPayload(notification)

        assertEquals(mapOf("type" to "ITEM_PARSING_COMPLETED", "refId" to "11", "kind" to "WISH"), data)
    }

    @Test
    fun `토너먼트 파싱 알림은 kind·tournamentId·tournamentItemId 를 모두 싣는다`() {
        val notification =
            Notification(
                UUID.randomUUID(),
                NotificationType.ITEM_PARSING_COMPLETED,
                "제목",
                "본문",
                11L,
                NotificationRouting.Tournament(tournamentId = 99L, tournamentItemId = 555L),
            )

        val data = FirebaseMessageSender.buildDataPayload(notification)

        assertEquals(
            mapOf(
                "type" to "ITEM_PARSING_COMPLETED",
                "refId" to "11",
                "kind" to "TOURNAMENT",
                "tournamentId" to "99",
                "tournamentItemId" to "555",
            ),
            data,
        )
    }
}
