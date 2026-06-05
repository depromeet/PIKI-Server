package com.depromeet.piki.notification.service

import com.depromeet.piki.notification.domain.Notification
import com.depromeet.piki.notification.domain.NotificationType
import com.depromeet.piki.notification.fcm.domain.UserDevice
import com.depromeet.piki.notification.fcm.repository.UserDeviceRepository
import com.depromeet.piki.notification.repository.NotificationRepository
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.StubFcmMessageSender
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 발송 채널(#245)을 실제 빈으로 검증한다. 도메인 publish -> dispatcher -> 채널 리스트 순회는 토대(#288)가 덮으므로,
// 여기서는 PushNotificationChannel 이 그 리스트에 합류하는지 + 토큰 조회·멀티캐스트 위임·죽은 토큰 정리를 검증한다.
// 외부 FCM 호출만 StubFcmMessageSender 로 격리하고(onSend 람다), 토큰 저장소·정리는 실제 DB(Testcontainers)다.
// 전달 토큰·호출 여부는 각 테스트의 onSend 람다에 캡처해 그 메서드만으로 시나리오가 완결되게 한다.
@Transactional
class PushNotificationChannelIntegrationTest : IntegrationTestSupport() {
    @Autowired private lateinit var channels: List<NotificationChannel>

    @Autowired private lateinit var pushNotificationChannel: PushNotificationChannel

    @Autowired private lateinit var userDeviceRepository: UserDeviceRepository

    @Autowired private lateinit var notificationRepository: NotificationRepository

    @Autowired private lateinit var stubFcmMessageSender: StubFcmMessageSender

    private fun saveNotification(userId: UUID): Notification =
        notificationRepository.save(
            Notification(
                userId = userId,
                type = NotificationType.ITEM_PARSING_COMPLETED,
                title = "파싱 완료",
                body = "상품 정보가 준비됐어요",
                refId = 1L,
            ),
        )

    @Test
    fun `PushNotificationChannel 은 dispatcher 의 채널 리스트에 합류한다`() {
        assertTrue(channels.any { it is PushNotificationChannel })
    }

    @Test
    fun `채널로 보내면 그 유저의 모든 기기 토큰으로 멀티캐스트 발송한다`() {
        val userId = UUID.randomUUID()
        userDeviceRepository.save(UserDevice(userId = userId, deviceId = "device-1", fcmToken = "token-1"))
        userDeviceRepository.save(UserDevice(userId = userId, deviceId = "device-2", fcmToken = "token-2"))
        var captured: List<String>? = null
        stubFcmMessageSender.onSend = { tokens, _ ->
            captured = tokens
            emptyList()
        }

        pushNotificationChannel.send(userId, saveNotification(userId))

        assertEquals(setOf("token-1", "token-2"), captured?.toSet())
    }

    @Test
    fun `발송 결과 죽은 토큰은 user_devices 에서 정리된다`() {
        val userId = UUID.randomUUID()
        userDeviceRepository.save(UserDevice(userId = userId, deviceId = "device-1", fcmToken = "live-token"))
        userDeviceRepository.save(UserDevice(userId = userId, deviceId = "device-2", fcmToken = "dead-token"))
        stubFcmMessageSender.onSend = { _, _ -> listOf("dead-token") }

        pushNotificationChannel.send(userId, saveNotification(userId))

        val remaining = userDeviceRepository.findAllByUserId(userId).map { it.fcmToken }
        assertEquals(listOf("live-token"), remaining)
    }

    @Test
    fun `토큰이 없는 유저면 외부 발송을 시도하지 않는다`() {
        val userId = UUID.randomUUID()
        var called = false
        stubFcmMessageSender.onSend = { _, _ ->
            called = true
            emptyList()
        }

        pushNotificationChannel.send(userId, saveNotification(userId))

        assertFalse(called)
    }
}
