package com.depromeet.piki.admin.announcement

import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

// 예약 공지 발송 폴링(#489). 발송 시각이 도래한 SCHEDULED 공지를 주기적으로 집어 async 발송한다.
// admin 켜진 환경(운영)에서만 뜬다 — 예약 발송도 백오피스 기능이라 ConditionalOnAdminEnabled.
// 단일 인스턴스 가정 — execute 내부 claim 이 상태를 SENDING 으로 전환해 다음 폴링의 중복 집음을 막는다(다중 인스턴스 방어는 후속).
@Component
@ConditionalOnAdminEnabled
class AnnouncementScheduler(
    private val announcementRepository: AnnouncementRepository,
    private val announcementSendService: AnnouncementSendService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    fun dispatchDue() {
        val due = announcementRepository.findByStatusAndScheduledAtLessThanEqual(Announcement.STATUS_SCHEDULED, LocalDateTime.now())
        if (due.isEmpty()) return
        log.info("예약 공지 발송 폴링 — 도래 {}건", due.size)
        // execute 는 @Async — 외부 빈 호출이라 proxy 를 거쳐 각자 별도 스레드에서 돈다.
        due.forEach { announcementSendService.execute(it.getId(), SYSTEM_ACTOR, clientIp = null) }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 60_000L
        private const val SYSTEM_ACTOR = "scheduler"
    }
}
