package com.depromeet.piki.admin.announcement

import com.depromeet.piki.notification.service.DeliveryStatus
import com.depromeet.piki.support.IntegrationTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertEquals

// 공지 발송 건별 결과(#489) 영속화·집계가 실제 MySQL 에서 도는지 검증한다.
// 회귀 가드: AnnouncementDelivery 는 BaseEntity 의 deleted_at 을 매핑하므로 마이그레이션에 그 컬럼이 빠지면
// save 가 Unknown column 으로 깨진다. 이 테스트가 실 INSERT 로 그걸 잡는다(ddl-auto:validate 만으론 새던 구멍).
// 결과 화면이 쓰는 상태별·코드별 집계 쿼리도 함께 검증한다.
@Transactional
class AnnouncementDeliveryPersistenceIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var deliveryRepository: AnnouncementDeliveryRepository

    @Test
    fun `건별 delivery 행을 저장하고 상태·FCM 코드별로 집계한다`() {
        val announcementId = 4_900_001L
        deliveryRepository.saveAll(
            listOf(
                AnnouncementDelivery(announcementId, UUID.randomUUID(), DeliveryStatus.SUCCESS, null),
                AnnouncementDelivery(announcementId, UUID.randomUUID(), DeliveryStatus.SUCCESS, null),
                AnnouncementDelivery(announcementId, UUID.randomUUID(), DeliveryStatus.FAILED, "UNREGISTERED"),
                AnnouncementDelivery(announcementId, UUID.randomUUID(), DeliveryStatus.FAILED, "SENDER_ID_MISMATCH"),
                AnnouncementDelivery(announcementId, UUID.randomUUID(), DeliveryStatus.NO_TOKEN, null),
                // 음수 케이스 — SUCCESS 인데 fcm_code 가 박힌 비정상 행(백필·수동수정·미래 코드변경). 실패 집계에 새면 안 된다.
                AnnouncementDelivery(announcementId, UUID.randomUUID(), DeliveryStatus.SUCCESS, "UNREGISTERED"),
            ),
        )

        assertEquals(6, deliveryRepository.countByAnnouncementId(announcementId))
        assertEquals(3, deliveryRepository.countByAnnouncementIdAndStatus(announcementId, DeliveryStatus.SUCCESS))
        assertEquals(2, deliveryRepository.countByAnnouncementIdAndStatus(announcementId, DeliveryStatus.FAILED))
        assertEquals(1, deliveryRepository.countByAnnouncementIdAndStatus(announcementId, DeliveryStatus.NO_TOKEN))

        // FCM 실패 사유 분포 — FAILED 행만 코드별로 센다. SUCCESS+UNREGISTERED 비정상 행은 제외돼 UNREGISTERED 가 2 로 부풀지 않는다.
        val byCode = deliveryRepository.countByFcmCode(announcementId).associate { it.code to it.count }
        assertEquals(mapOf("UNREGISTERED" to 1L, "SENDER_ID_MISMATCH" to 1L), byCode)
    }
}
