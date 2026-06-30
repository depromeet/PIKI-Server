package com.depromeet.piki.admin.announcement

import com.depromeet.piki.notification.domain.NotificationType
import com.depromeet.piki.notification.repository.NotificationRepository
import com.depromeet.piki.notification.service.AnnouncementBroadcaster
import com.depromeet.piki.notification.service.DeliveryStatus
import com.depromeet.piki.notification.service.RecipientDelivery
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.user.service.UserService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 공지 전역 노출(#560) — 토큰 없는 게스트도 푸시는 못 받아도 알림센터(notification row)로 공지를 받는지 검증한다.
// 핵심 변경: 공지 fan-out 대상이 "토큰 보유자"에서 "활성 유저 전체(게스트 포함)"로 바뀌었고, broadcaster 는 토큰 없는
// 수신자에게도 알림 row + SSE 를 만들고 FCM 만 건너뛴다.
@Transactional
class AnnouncementGuestNotificationIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var broadcaster: AnnouncementBroadcaster

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Test
    fun `공지 알림센터 fan-out 대상은 토큰 없는 게스트를 포함한다`() {
        val guest = userService.createGuest()
        assertTrue(
            guest.getId() in userService.findAllActiveUserIds(),
            "게스트(FCM 토큰 없음)도 공지 알림센터 fan-out 대상에 포함돼야 한다",
        )
    }

    @Test
    fun `토큰 없는 게스트에게도 공지 알림 row 를 만들고 FCM 푸시는 보내지 않는다`() {
        val guest = userService.createGuest() // 기기 토큰 미등록

        val deliveries = mutableListOf<RecipientDelivery>()
        broadcaster.broadcast(
            pushTitle = "공지 제목",
            pushBody = "공지 내용",
            pushEnabled = true,
            refId = 560L,
            recipients = listOf(guest.getId()),
        ) { deliveries += it }

        // 알림센터: 토큰이 없어도 ANNOUNCEMENT 알림 row 가 생성돼야 한다 (앱을 열면 벨에서 보임).
        val page = notificationRepository.findPage(guest.getId(), null, 10, null)
        assertEquals(1, page.size, "토큰 없는 게스트도 알림센터 row 를 받아야 한다")
        assertEquals(NotificationType.ANNOUNCEMENT, page.first().type)

        // 푸시: 토큰이 없어 FCM 은 미발송(NO_TOKEN) — sender 미설정 환경이면 SKIPPED. 어느 쪽이든 SUCCESS/FAILED 가 아니어야 한다.
        assertEquals(1, deliveries.size)
        assertTrue(
            deliveries.single().status in setOf(DeliveryStatus.NO_TOKEN, DeliveryStatus.SKIPPED),
            "토큰 없는 수신자의 FCM 도달 상태는 NO_TOKEN(또는 미설정 시 SKIPPED)이어야 한다",
        )
    }
}
