package com.depromeet.piki.admin.announcement

import com.depromeet.piki.admin.audit.AdminAuditAction
import com.depromeet.piki.admin.audit.AdminAuditService
import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import com.depromeet.piki.notification.fcm.service.UserDeviceService
import com.depromeet.piki.notification.service.DeliveryStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

// 공지 등록·예약·발송·결과(#391/#489). 발송 시 자연어를 새로 입력받지 않는다 — 미리 "등록"한 공지만 골라
// 즉시/예약 발송한다(오타·실수 방지). 실제 fan-out·진행률·집계는 AnnouncementSendService(async)·Broadcaster 가 맡는다.
@Service
@ConditionalOnAdminEnabled
class AdminAnnouncementService(
    private val announcementRepository: AnnouncementRepository,
    private val deliveryRepository: AnnouncementDeliveryRepository,
    private val userDeviceService: UserDeviceService,
    private val sendService: AnnouncementSendService,
    private val auditService: AdminAuditService,
) {
    // 목록(최신순) 페이징 — 누적되는 발송 내역을 한 페이지(PAGE_SIZE)씩만 로드한다.
    fun list(page: Int): Page<Announcement> = announcementRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page.coerceAtLeast(0), PAGE_SIZE))

    fun get(id: Long): Announcement =
        announcementRepository.findById(id).orElseThrow { IllegalArgumentException("공지를 찾을 수 없습니다.") }

    // 발송 대상자 추출 — 토큰 보유자 수. 예약/발송 확인 페이지가 발송 전에 미리 보여준다(대상자 추출 단계).
    fun recipientCount(): Long = userDeviceService.countTokenHolders()

    // 공지 초안 등록 — 발송 전 상태(DRAFT). 발송/예약은 schedule() 로만.
    @Transactional
    fun register(
        title: String,
        body: String,
    ): Announcement = announcementRepository.save(Announcement(title = title, body = body, target = TARGET_ALL))

    // 발송 예약/즉시 발송 — scheduledAt 이 null 이면 지금 발송, 있으면 그 시각으로 예약(스케줄러가 발송).
    // 즉시 발송은 async execute 를 직접 호출(claim 이 DRAFT→SENDING 전환, 별도 트랜잭션). 예약은 여기서 SCHEDULED 로 박는다.
    // (reserve 를 별도 메서드로 빼면 self-invocation 으로 @Transactional 이 무력화되므로 이 메서드에 트랜잭션을 둔다.)
    @Transactional
    fun schedule(
        id: Long,
        scheduledAt: LocalDateTime?,
        actor: String,
        clientIp: String?,
    ) {
        val announcement = get(id)
        require(announcement.isDraft) { "이미 발송됐거나 예약된 공지입니다." }
        scheduledAt ?: run {
            // 즉시 발송 — async fan-out 으로 바로 보낸다(claim 이 DRAFT→SENDING). 여기선 DB 를 건드리지 않는다.
            sendService.execute(id, actor, clientIp)
            return
        }
        // scheduledAt 은 운영자가 입력한 KST wall-clock 이므로 KST 기준으로 미래인지 본다(서버 JVM TZ 와 무관).
        require(scheduledAt.isAfter(LocalDateTime.now(Announcement.KST))) { "예약 시각은 현재보다 미래여야 합니다." }
        announcement.schedule(scheduledAt)
        announcementRepository.save(announcement)
        auditService.record(actor, AdminAuditAction.ANNOUNCEMENT_SEND, "공지 발송 예약 id=$id at=$scheduledAt", clientIp)
    }

    // 예약 취소 — SCHEDULED → DRAFT 로 되돌린다(다시 편집·삭제·재예약 가능).
    // claim 과 같은 비관적 락으로 조회한다 — 락 없이 get() 하면, 운영자가 취소를 누른 순간 스케줄러 claim 이
    // 같은 SCHEDULED 를 집어 SENDING 으로 전환하는 경합에서 상태가 어긋날 수 있다(취소했는데 발송 시작 등).
    @Transactional
    fun cancelSchedule(
        id: Long,
        actor: String,
        clientIp: String?,
    ) {
        val announcement =
            announcementRepository.findByIdForUpdate(id) ?: throw IllegalArgumentException("공지를 찾을 수 없습니다.")
        announcement.cancelSchedule()
        announcementRepository.save(announcement)
        auditService.record(actor, AdminAuditAction.ANNOUNCEMENT_SEND, "공지 예약 취소 id=$id", clientIp)
    }

    // 등록한 초안 삭제. 발송된·예약된 공지는 삭제하지 않는다(예약은 먼저 취소).
    @Transactional
    fun delete(id: Long) {
        val announcement = get(id)
        // DRAFT(미발송 초안) 또는 MISSED(유예 초과 미발송)만 삭제 가능 — 둘 다 발송 안 된 정리 대상이다.
        // SENT/SENDING(발송 내역)·SCHEDULED(예약, 먼저 취소)는 삭제 불가.
        require(announcement.isDraft || announcement.isMissed) { "발송됐거나 예약된 공지는 삭제할 수 없습니다(예약은 먼저 취소)." }
        announcementRepository.delete(announcement)
    }

    // 발송 결과 집계 — 건별 delivery 행을 상태·코드별로 합산해 화면/폴링에 내린다(건별은 노출 안 함).
    @Transactional(readOnly = true)
    fun result(id: Long): AnnouncementResult {
        val announcement = get(id)
        val noToken = deliveryRepository.countByAnnouncementIdAndStatus(id, DeliveryStatus.NO_TOKEN)
        val failureByCode = deliveryRepository.countByFcmCode(id).map { it.code to it.count }
        return AnnouncementResult(
            id = id,
            title = announcement.title,
            body = announcement.body,
            status = announcement.status,
            done = announcement.isSent,
            scheduledAt = announcement.scheduledAt,
            sentAt = announcement.sentAt,
            total = announcement.totalCount,
            success = announcement.successCount,
            failure = announcement.failureCount,
            skipped = announcement.skippedCount,
            processed = announcement.processedCount,
            progressPercent = announcement.progressPercent,
            noTokenCount = noToken,
            failureByCode = failureByCode,
        )
    }

    companion object {
        private const val TARGET_ALL = "토큰 보유자 전체"
        private const val PAGE_SIZE = 20
    }
}

// 발송 결과 집계 뷰(화면 + 폴링 JSON 공용). 건별 행은 담지 않고 합산값만.
data class AnnouncementResult(
    val id: Long,
    val title: String,
    val body: String,
    val status: String,
    val done: Boolean,
    val scheduledAt: LocalDateTime?,
    val sentAt: LocalDateTime?,
    val total: Int,
    val success: Int,
    val failure: Int,
    val skipped: Int,
    val processed: Int,
    val progressPercent: Int,
    val noTokenCount: Long,
    // FCM 실패 사유 분포: (코드, 수). 예: ("UNREGISTERED", 3), ("SENDER_ID_MISMATCH", 1)
    val failureByCode: List<Pair<String, Long>>,
)
