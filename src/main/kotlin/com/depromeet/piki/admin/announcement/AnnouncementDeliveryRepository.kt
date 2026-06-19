package com.depromeet.piki.admin.announcement

import com.depromeet.piki.notification.service.DeliveryStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AnnouncementDeliveryRepository : JpaRepository<AnnouncementDelivery, Long> {
    fun countByAnnouncementId(announcementId: Long): Long

    fun countByAnnouncementIdAndStatus(
        announcementId: Long,
        status: DeliveryStatus,
    ): Long

    // 실패 사유 분포 — FAILED 행만 FCM 에러코드별로 센다(결과 화면의 "UNREGISTERED 3 · SENDER_ID_MISMATCH 1").
    // status=FAILED 를 명시한다 — fcm_code IS NOT NULL 만으론, 비정상 데이터(SUCCESS 인데 코드가 박힌 백필·수동수정 등)가
    // 실패 집계를 부풀릴 수 있다. "실패 사유"라는 의도를 쿼리에 못 박아 그런 행을 배제한다.
    @Query(
        "SELECT d.fcmCode AS code, COUNT(d) AS count FROM AnnouncementDelivery d " +
            "WHERE d.announcementId = :announcementId AND d.status = com.depromeet.piki.notification.service.DeliveryStatus.FAILED " +
            "AND d.fcmCode IS NOT NULL GROUP BY d.fcmCode",
    )
    fun countByFcmCode(
        @Param("announcementId") announcementId: Long,
    ): List<FcmCodeCount>
}

// 인터페이스 프로젝션 — JPQL 의 별칭(code·count)으로 매핑된다.
interface FcmCodeCount {
    val code: String
    val count: Long
}
