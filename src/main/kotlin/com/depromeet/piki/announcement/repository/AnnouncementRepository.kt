package com.depromeet.piki.announcement.repository

import com.depromeet.piki.announcement.domain.Announcement
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

// 공지 저장소(중립 도메인). admin 이 작성·발송에 쓰고, 유저 조회(공지사항 페이지·딥링크)도 같은 도메인을 단방향 참조한다.
interface AnnouncementRepository : JpaRepository<Announcement, Long> {
    // 목록은 누적되므로 페이징한다(최신순). 한 페이지씩만 로드.
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<Announcement>

    // ── 유저 조회(#556) — 발송 완료(SENT)된 공지만 노출한다. DRAFT/SCHEDULED/SENDING/MISSED 는 보이지 않는다. ──
    // 단건(딥링크 착지). SENT 가 아니거나 없으면 null → 서비스가 404 로 변환(미발송 공지의 존재를 노출하지 않음).
    fun findByIdAndStatus(
        id: Long,
        status: String,
    ): Announcement?

    // 목록 첫 페이지(커서 없음) — SENT 만 최신순(id desc). limit 는 Pageable 로 +1 조회해 hasNext 판정.
    fun findByStatusOrderByIdDesc(
        status: String,
        pageable: Pageable,
    ): List<Announcement>

    // 목록 다음 페이지 — cursor(id) 이전(더 오래된) SENT 만 최신순.
    fun findByStatusAndIdLessThanOrderByIdDesc(
        status: String,
        id: Long,
        pageable: Pageable,
    ): List<Announcement>

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
