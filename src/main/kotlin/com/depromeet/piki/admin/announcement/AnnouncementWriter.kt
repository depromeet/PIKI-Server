package com.depromeet.piki.admin.announcement

import com.depromeet.piki.admin.audit.AdminAuditAction
import com.depromeet.piki.admin.audit.AdminAuditService
import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import com.depromeet.piki.announcement.domain.Announcement
import com.depromeet.piki.announcement.repository.AnnouncementRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 공지 영속화 전용 빈(#561). 이미지 rehost 같은 외부 호출은 AdminAnnouncementService 가 트랜잭션 밖에서 끝내고,
// DB 쓰기만 이 빈의 짧은 @Transactional 에 위임한다 — self-invocation 으로 트랜잭션이 무력화되지 않도록 별도 빈으로 분리
// (## 트랜잭션 경계: 외부 호출은 트랜잭션 밖 + self-invocation 회피).
@Service
@ConditionalOnAdminEnabled
class AnnouncementWriter(
    private val announcementRepository: AnnouncementRepository,
    private val auditService: AdminAuditService,
) {
    // 초안 생성 — rehost 전 원본 body 로 먼저 저장해 id 를 확보한다(이미지 키 announcement/{id}/ 에 필요).
    @Transactional
    fun createDraft(
        title: String,
        body: String,
        pushEnabled: Boolean,
        pushTitle: String,
        pushBody: String,
        actor: String,
        clientIp: String?,
    ): Announcement {
        val announcement =
            announcementRepository.save(
                Announcement(
                    title = title,
                    body = body,
                    target = TARGET_ALL,
                    pushEnabled = pushEnabled,
                    pushTitle = pushTitle,
                    pushBody = pushBody,
                ),
            )
        auditService.record(actor, AdminAuditAction.ANNOUNCEMENT_REGISTER, "공지 초안 등록 id=${announcement.getId()}", clientIp)
        return announcement
    }

    // 초안 수정(DRAFT 만, 엔티티 edit 가 강제). 비관적 락으로 조회해 발송 claim 과의 경합을 직렬화한다.
    @Transactional
    fun applyEdit(
        id: Long,
        title: String,
        body: String,
        pushEnabled: Boolean,
        pushTitle: String,
        pushBody: String,
        actor: String,
        clientIp: String?,
    ): Announcement {
        val announcement = announcementRepository.findByIdForUpdate(id) ?: throw IllegalArgumentException("공지를 찾을 수 없습니다.")
        announcement.edit(title, body, pushEnabled, pushTitle, pushBody)
        announcementRepository.save(announcement)
        auditService.record(actor, AdminAuditAction.ANNOUNCEMENT_EDIT, "공지 초안 수정 id=$id", clientIp)
        return announcement
    }

    // 초안 삭제 — DRAFT/MISSED 만(미발송 정리 대상). 발송·예약 건은 삭제 불가.
    @Transactional
    fun deleteDraft(id: Long) {
        val announcement = announcementRepository.findById(id).orElseThrow { IllegalArgumentException("공지를 찾을 수 없습니다.") }
        require(announcement.isDraft || announcement.isMissed) { "발송됐거나 예약된 공지는 삭제할 수 없습니다(예약은 먼저 취소)." }
        announcementRepository.delete(announcement)
    }

    companion object {
        const val TARGET_ALL = "토큰 보유자 전체"
    }
}
