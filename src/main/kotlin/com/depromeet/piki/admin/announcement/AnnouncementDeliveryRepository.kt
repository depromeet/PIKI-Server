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

    // 실패 사유 분포 — FAILED 행을 FCM 에러코드별로 센다(결과 화면의 "UNREGISTERED 3 · SENDER_ID_MISMATCH 1").
    @Query(
        "SELECT d.fcmCode AS code, COUNT(d) AS count FROM AnnouncementDelivery d " +
            "WHERE d.announcementId = :announcementId AND d.fcmCode IS NOT NULL GROUP BY d.fcmCode",
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
