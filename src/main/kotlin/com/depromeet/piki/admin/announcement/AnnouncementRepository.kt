package com.depromeet.piki.admin.announcement

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

// 공지 저장소. 발송 내역은 최신순으로 본다.
interface AnnouncementRepository : JpaRepository<Announcement, Long> {
    // 목록은 누적되므로 페이징한다(최신순). 한 페이지씩만 로드.
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<Announcement>

    // SENDING 클레임용 비관적 락(SELECT ... FOR UPDATE). claim 의 조회→상태검사→저장을 한 트랜잭션 안에서
    // 직렬화해, 더블클릭·스케줄러/수동 경합으로 같은 공지가 두 번 SENDING 으로 전환돼 전체 유저에게 중복
    // 발송되는 것을 막는다(상태 전이 원자화).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Announcement a where a.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: Long,
    ): Announcement?

    // 예약 폴링(#489) — 발송 시각이 도래한 SCHEDULED 공지. 스케줄러가 집어 발송한다.
    fun findByStatusAndScheduledAtLessThanEqual(
        status: String,
        scheduledAt: LocalDateTime,
    ): List<Announcement>
}
