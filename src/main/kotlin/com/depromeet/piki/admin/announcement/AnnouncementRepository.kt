package com.depromeet.piki.admin.announcement

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

// 공지 저장소. 발송 내역은 최신순으로 본다.
interface AnnouncementRepository : JpaRepository<Announcement, Long> {
    fun findAllByOrderByCreatedAtDesc(): List<Announcement>

    // 예약 폴링(#489) — 발송 시각이 도래한 SCHEDULED 공지. 스케줄러가 집어 발송한다.
    fun findByStatusAndScheduledAtLessThanEqual(
        status: String,
        scheduledAt: LocalDateTime,
    ): List<Announcement>
}
