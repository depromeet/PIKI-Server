package com.depromeet.piki.admin.announcement

import com.depromeet.piki.notification.domain.NotificationType
import com.depromeet.piki.notification.fcm.domain.UserDevice
import com.depromeet.piki.notification.fcm.repository.UserDeviceJpaRepository
import com.depromeet.piki.notification.repository.NotificationJpaRepository
import com.depromeet.piki.notification.service.AnnouncementBroadcaster
import com.depromeet.piki.notification.service.DeliveryStatus
import com.depromeet.piki.notification.service.RecipientDelivery
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.StubFcmMessageSender
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// 공지 예약·발송 흐름(#489)을 실 MySQL 에서 검증한다 — overdue 정리, 중복 claim 차단, fan-out 팬아웃.
// 단일 공유 컨텍스트(admin.enabled=true)에서 돌며, 스케줄러 자동 폴링은 꺼두고 dispatchDue() 를 직접 호출해 결정적으로 본다.
@Transactional
class AnnouncementSchedulingIntegrationTest : IntegrationTestSupport() {
    @Autowired private lateinit var announcementRepository: AnnouncementRepository

    @Autowired private lateinit var progressWriter: AnnouncementProgressWriter

    @Autowired private lateinit var scheduler: AnnouncementScheduler

    @Autowired private lateinit var broadcaster: AnnouncementBroadcaster

    @Autowired private lateinit var userDeviceJpaRepository: UserDeviceJpaRepository

    @Autowired private lateinit var notificationJpaRepository: NotificationJpaRepository

    @Autowired private lateinit var stubFcm: StubFcmMessageSender

    @Test
    fun `유예시간을 넘긴 예약은 MISSED 로 정리하고 발송하지 않는다`() {
        val announcement = announcementRepository.save(Announcement("점검 안내", "내용", "토큰 보유자 전체"))
        announcement.schedule(LocalDateTime.now(Announcement.KST).minusHours(3)) // 유예(1h) 한참 초과
        announcementRepository.save(announcement)

        scheduler.dispatchDue()

        assertEquals(Announcement.STATUS_MISSED, announcementRepository.findById(announcement.getId()).get().status)
    }

    @Test
    fun `유예시간 안에 도래한 예약은 SENDING 으로 클레임된다`() {
        val announcement = announcementRepository.save(Announcement("공지", "내용", "토큰 보유자 전체"))
        announcement.schedule(LocalDateTime.now(Announcement.KST).minusMinutes(1)) // 1분 전(유예 안)
        announcementRepository.save(announcement)

        // 도래·유예 분기만 본다(execute 는 @Async). claim 으로 직접 검증.
        assertNotNull(progressWriter.claim(announcement.getId(), "scheduler"))
        assertEquals(Announcement.STATUS_SENDING, announcementRepository.findById(announcement.getId()).get().status)
    }

    @Test
    fun `claim 은 한 번만 성공하고 두 번째는 null 이다 (중복 발송 차단)`() {
        val announcement = announcementRepository.save(Announcement("공지", "내용", "토큰 보유자 전체"))

        val first = progressWriter.claim(announcement.getId(), "운영자")
        val second = progressWriter.claim(announcement.getId(), "운영자")

        assertNotNull(first)
        assertNull(second)
        assertEquals(Announcement.STATUS_SENDING, announcementRepository.findById(announcement.getId()).get().status)
    }

    @Test
    fun `broadcast 는 토큰 보유자에게 알림을 만들고 FCM 성공이면 delivery 가 SUCCESS 다`() {
        val userId = UUID.randomUUID()
        userDeviceJpaRepository.save(UserDevice(userId = userId, deviceId = "device-1", fcmToken = "tok-${UUID.randomUUID()}"))
        stubFcm.onSend = { _, _ -> emptyList() } // 죽은 토큰 없음 → 전부 성공

        val deliveries = mutableListOf<RecipientDelivery>()
        broadcaster.broadcast("공지 제목", "공지 본문", refId = 999_001L, recipients = listOf(userId)) { deliveries += it }

        assertEquals(1, deliveries.size)
        assertEquals(DeliveryStatus.SUCCESS, deliveries.single().status)
        // 히스토리(알림)도 그 유저에게 ANNOUNCEMENT 로 생성된다.
        val created = notificationJpaRepository.findAll().count { it.userId == userId && it.type == NotificationType.ANNOUNCEMENT }
        assertEquals(1, created)
    }
}
