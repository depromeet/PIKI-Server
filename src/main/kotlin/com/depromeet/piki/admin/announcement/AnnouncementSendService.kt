package com.depromeet.piki.admin.announcement

import com.depromeet.piki.admin.audit.AdminAuditAction
import com.depromeet.piki.admin.audit.AdminAuditService
import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import com.depromeet.piki.notification.service.AnnouncementBroadcaster
import com.depromeet.piki.notification.service.DeliveryStatus
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

// 공지 async fan-out 오케스트레이션(#489). 외부 진입(컨트롤러 즉시 발송 · 스케줄러 예약 도래)이 이 빈의 @Async execute 를
// 직접 호출한다 — proxy 를 거쳐 별도 스레드에서 돈다(self-invocation 회피를 위해 wrapper 를 두지 않는다).
// SENDING 클레임·진행률·delivery 저장은 AnnouncementProgressWriter 의 짧은 트랜잭션에, 실제 알림 생성·발송은
// AnnouncementBroadcaster(notification 도메인)에 위임한다. 진행률은 배치(FLUSH_SIZE)마다 갱신해 폴링이 % 를 본다.
@Service
@ConditionalOnAdminEnabled
class AnnouncementSendService(
    private val broadcaster: AnnouncementBroadcaster,
    private val progressWriter: AnnouncementProgressWriter,
    private val auditService: AdminAuditService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    fun execute(
        announcementId: Long,
        actor: String,
        clientIp: String?,
    ) {
        val claim =
            progressWriter.claim(announcementId, actor) ?: run {
                log.info("공지 발송 스킵 — 클레임 실패(이미 처리 중·완료·없음) announcementId={}", announcementId)
                return
            }
        log.info("공지 발송 시작 announcementId={} 대상={}명", announcementId, claim.recipients.size)

        val buffer = ArrayList<AnnouncementDelivery>(FLUSH_SIZE)
        var success = 0
        var failure = 0
        var skipped = 0
        runCatching {
            broadcaster.broadcast(
                pushTitle = claim.pushTitle,
                pushBody = claim.pushBody,
                pushEnabled = claim.pushEnabled,
                refId = announcementId,
                recipients = claim.recipients,
            ) { delivery ->
                buffer += AnnouncementDelivery(announcementId, delivery.userId, delivery.status, delivery.fcmCode)
                when (delivery.status) {
                    DeliveryStatus.SUCCESS -> success++
                    DeliveryStatus.FAILED -> failure++
                    DeliveryStatus.NO_TOKEN, DeliveryStatus.SKIPPED -> skipped++
                }
                if (buffer.size >= FLUSH_SIZE) {
                    progressWriter.flush(announcementId, buffer.toList(), success, failure, skipped)
                    buffer.clear()
                    success = 0
                    failure = 0
                    skipped = 0
                }
            }
        }.onFailure { log.error("공지 fan-out 중 오류 announcementId={} — 진행분까지 기록 후 완료 처리", announcementId, it) }

        // 남은 버퍼 flush + SENT 고정(부분 실패여도 진행분은 보존하고 완료로 닫는다).
        progressWriter.finish(announcementId, buffer.toList(), success, failure, skipped)
        auditService.record(actor, AdminAuditAction.ANNOUNCEMENT_SEND, "공지 발송 완료 announcementId=$announcementId", clientIp)
        log.info("공지 발송 완료 announcementId={}", announcementId)
    }

    companion object {
        // 진행률·delivery 행을 몇 수신자마다 DB 에 내릴지. 너무 작으면 트랜잭션이 잦고, 너무 크면 진행률이 뜸하게 갱신된다.
        private const val FLUSH_SIZE = 50
    }
}
