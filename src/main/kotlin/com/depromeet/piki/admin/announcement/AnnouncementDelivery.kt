package com.depromeet.piki.admin.announcement

import com.depromeet.piki.common.domain.LongBaseEntity
import com.depromeet.piki.notification.service.DeliveryStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.util.UUID

// 공지 발송 건별 결과(#489) — 수신자(userId) 1명당 1행. UI 엔 집계만 보여주고, 이 건별 행은 추후 analytics 기반이다
// (notifications.is_read 와 user_id + announcement_id 로 JOIN 해 "전송 성공 대비 클릭" 퍼널 분석). FK 제약 없음(논리적 참조).
@Entity
@Table(name = "announcement_deliveries")
class AnnouncementDelivery(
    announcementId: Long,
    userId: UUID,
    status: DeliveryStatus,
    fcmCode: String?,
) : LongBaseEntity() {
    @Column(name = "announcement_id", nullable = false)
    val announcementId: Long = announcementId

    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    val userId: UUID = userId

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    val status: DeliveryStatus = status

    // 실패 시 대표 FCM 에러코드. 성공·토큰없음·스킵은 null.
    @Column(name = "fcm_code", length = 50)
    val fcmCode: String? = fcmCode
}
