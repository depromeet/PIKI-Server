package com.depromeet.piki.admin.announcement
import com.depromeet.piki.announcement.domain.Announcement
import com.depromeet.piki.announcement.repository.AnnouncementRepository

import com.depromeet.piki.admin.audit.AdminAuditAction
import com.depromeet.piki.admin.audit.AdminAuditService
import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import com.depromeet.piki.announcement.domain.AnnouncementImageFile
import com.depromeet.piki.common.storage.ImageStorage
import com.depromeet.piki.notification.fcm.service.UserDeviceService
import com.depromeet.piki.notification.service.DeliveryStatus
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

// 공지 등록·예약·발송·결과(#391/#489). 발송 시 자연어를 새로 입력받지 않는다 — 미리 "등록"한 공지만 골라
// 즉시/예약 발송한다(오타·실수 방지). 실제 fan-out·진행률·집계는 AnnouncementSendService(async)·Broadcaster 가 맡는다.
// 등록·수정 시 본문의 외부 이미지를 우리 S3 로 rehost(#561)하는데, fetch·업로드·삭제는 외부 호출이라
// 트랜잭션 밖에서 끝내고 영속화만 AnnouncementWriter(짧은 @Transactional)에 위임한다(## 트랜잭션 경계).
@Service
@ConditionalOnAdminEnabled
class AdminAnnouncementService(
    private val announcementRepository: AnnouncementRepository,
    private val deliveryRepository: AnnouncementDeliveryRepository,
    private val userDeviceService: UserDeviceService,
    private val sendService: AnnouncementSendService,
    private val auditService: AdminAuditService,
    private val writer: AnnouncementWriter,
    private val imageRehoster: AnnouncementImageRehoster,
    private val imageStorage: ImageStorage,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    // 목록(최신순) 페이징 — 누적되는 발송 내역을 한 페이지(PAGE_SIZE)씩만 로드한다.
    fun list(page: Int): Page<Announcement> = announcementRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page.coerceAtLeast(0), PAGE_SIZE))

    fun get(id: Long): Announcement =
        announcementRepository.findById(id).orElseThrow { IllegalArgumentException("공지를 찾을 수 없습니다.") }

    // 발송 대상자 추출 — 토큰 보유자 수. 예약/발송 확인 페이지가 발송 전에 미리 보여준다(대상자 추출 단계).
    fun recipientCount(): Long = userDeviceService.countTokenHolders()

    // 빈 초안 생성(stub-create #561) — "새 공지 작성" 이 부른다. 제목·본문·이미지·푸시는 수정 화면(update)에서 채운다.
    // 이미지 즉시 업로드(addImageBlobHook)가 announcement/{id}/ 키를 쓰려면 작성 시점에 id 가 있어야 하므로,
    // 빈 초안을 먼저 만들어 id 를 확보한 뒤 수정 화면으로 보낸다(WordPress auto-draft 방식). 발송 전 단계도 audit 대상이라 writer 가 남긴다.
    fun register(
        actor: String,
        clientIp: String?,
    ): Announcement = writer.createDraft(PLACEHOLDER_TITLE, "", pushEnabled = true, pushTitle = "", pushBody = "", actor = actor, clientIp = clientIp)

    // 본문 이미지 즉시 업로드(#561) — 수정 화면 에디터가 로컬 파일을 드롭하면 호출한다. DRAFT 만(발송된 공지 본문은 고정).
    // DB 쓰기가 없어(본문 반영은 저장 시점) 트랜잭션을 두지 않는다 — 형식 검증 후 S3 업로드(외부 호출)만 하고 URL 을 돌려준다.
    fun uploadImage(
        id: Long,
        bytes: ByteArray,
        contentType: String?,
    ): String {
        require(get(id).isDraft) { "발송됐거나 예약된 공지에는 이미지를 추가할 수 없습니다." }
        val image = AnnouncementImageFile.of(bytes, contentType)
        val key = "announcement/$id/${UUID.randomUUID()}.${image.extension}"
        val url = imageStorage.upload(image.bytes, key, image.mimeType)
        log.info("공지 이미지 업로드 완료: announcementId={}, key={}", id, key) // 아바타 업로드와 동일한 결의 업로드 성공 로그
        return url
    }

    // 초안 수정 — DRAFT 만(엔티티 edit 가 강제). 발송 전 오타 교정·다듬기(#561).
    // rehost(외부 호출)는 트랜잭션 밖에서 끝낸 뒤 writer 의 짧은 트랜잭션(비관적 락)에 영속화를 위임한다.
    // rehost 전 DRAFT 사전 확인 — 비-DRAFT 면 이미지 업로드를 낭비하지 않고 곧장 막는다(authoritative 검증은 edit 가 락 안에서 재확인).
    fun update(
        id: Long,
        title: String,
        body: String,
        pushEnabled: Boolean,
        pushTitle: String,
        pushBody: String,
        actor: String,
        clientIp: String?,
    ): Announcement {
        require(get(id).isDraft) { "발송됐거나 예약된 공지는 수정할 수 없습니다." }
        val rehostedBody = imageRehoster.rehost(id, body) // 트랜잭션 밖 (외부 호출)
        return writer.applyEdit(id, title, rehostedBody, pushEnabled, pushTitle, pushBody, actor, clientIp)
    }

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
        auditService.record(actor, AdminAuditAction.ANNOUNCEMENT_SCHEDULE, "공지 발송 예약 id=$id at=$scheduledAt", clientIp)
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
        auditService.record(actor, AdminAuditAction.ANNOUNCEMENT_SCHEDULE_CANCEL, "공지 예약 취소 id=$id", clientIp)
    }

    // 등록한 초안 삭제. 발송된·예약된 공지는 삭제하지 않는다(예약은 먼저 취소). DB 삭제(tx)는 writer 에 위임하고,
    // 그 공지의 S3 이미지(announcement/{id}/)는 트랜잭션 밖에서 prefix 통째로 정리한다(외부 호출).
    // S3 정리 실패는 best-effort 로 로그만 남긴다 — DB 행은 이미 지워졌고, 남은 객체는 orphan(무해)일 뿐이다.
    fun delete(id: Long) {
        writer.deleteDraft(id)
        runCatching { imageStorage.deleteByPrefix("announcement/$id/") }
            .onFailure { log.warn("공지 이미지 S3 정리 실패(orphan 가능): announcementId={}, error={}", id, it.message) }
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
        private const val PAGE_SIZE = 20

        // 빈 초안(stub-create)의 임시 제목 — 엔티티가 title 비어있음을 막으므로 placeholder 를 둔다. 운영자가 수정 화면에서 바꾼다.
        private const val PLACEHOLDER_TITLE = "(제목 없는 공지)"
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
