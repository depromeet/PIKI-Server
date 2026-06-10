package com.depromeet.piki.notification.fcm.service

import com.depromeet.piki.notification.domain.Notification
import com.depromeet.piki.notification.domain.NotificationRouting
import com.depromeet.piki.notification.domain.NotificationType
import com.depromeet.piki.support.withId
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
    // 시스템 알림(actor 없음)의 imageUrl 을 채우는 기본 아바타 — 운영에선 DefaultPushImage 가 publicBaseUrl 로 조립한다.
    private val defaultPushImageUrl = "https://img.test/defaults/push-icon.svg"

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
    // id 는 항상 실린다(채널 무관 dedup·푸시 탭→읽음의 키, #246) — 미영속 엔티티엔 withId 로 부여한다.
    @Test
    fun `라우팅 없는 알림은 id·type·category·imageUrl·refId 를 data 에 싣는다`() {
        val notification = Notification(UUID.randomUUID(), NotificationType.TOURNAMENT_JOINED, "제목", "본문", 7L).withId(42L)

        val data = FirebaseMessageSender.buildDataPayload(notification, defaultPushImageUrl)

        // actor 스냅샷 없음 → imageUrl 은 defaultPushImageUrl 로 채워진다. TOURNAMENT_JOINED → category=ACTIVITY.
        assertEquals(
            mapOf("id" to "42", "type" to "TOURNAMENT_JOINED", "category" to "ACTIVITY", "imageUrl" to defaultPushImageUrl, "refId" to "7"),
            data,
        )
    }

    @Test
    fun `actor 스냅샷이 있으면 imageUrl 은 그 프사 URL 이다`() {
        val notification =
            Notification(UUID.randomUUID(), NotificationType.TOURNAMENT_ITEM_ADDED, "제목", "본문", 7L, actorImageUrl = "https://img.test/profiles/a.png")
                .withId(45L)

        val data = FirebaseMessageSender.buildDataPayload(notification, defaultPushImageUrl)

        assertEquals("https://img.test/profiles/a.png", data["imageUrl"])
        assertEquals("ACTIVITY", data["category"])
    }

    @Test
    fun `위시 파싱 알림은 kind=WISH 만 더 싣고 토너먼트 키는 생략한다`() {
        val notification =
            Notification(UUID.randomUUID(), NotificationType.ITEM_PARSING_COMPLETED, "제목", "본문", 11L, NotificationRouting.Wish)
                .withId(43L)

        val data = FirebaseMessageSender.buildDataPayload(notification, defaultPushImageUrl)

        // ITEM_PARSING_* → category=SYSTEM. actor 없어 imageUrl=default.
        assertEquals(
            mapOf(
                "id" to "43",
                "type" to "ITEM_PARSING_COMPLETED",
                "category" to "SYSTEM",
                "imageUrl" to defaultPushImageUrl,
                "refId" to "11",
                "kind" to "WISH",
            ),
            data,
        )
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
            ).withId(44L)

        val data = FirebaseMessageSender.buildDataPayload(notification, defaultPushImageUrl)

        assertEquals(
            mapOf(
                "id" to "44",
                "type" to "ITEM_PARSING_COMPLETED",
                "category" to "SYSTEM",
                "imageUrl" to defaultPushImageUrl,
                "refId" to "11",
                "kind" to "TOURNAMENT",
                "tournamentId" to "99",
                "tournamentItemId" to "555",
            ),
            data,
        )
    }
}
