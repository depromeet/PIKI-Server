package com.depromeet.piki.admin.announcement

import com.depromeet.piki.notification.fcm.service.UserDeviceService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// 공지 발송의 짧은 트랜잭션 경계(#489). async fan-out(AnnouncementSendService.execute)은 외부 FCM 호출이 섞여
// 트랜잭션 밖에서 돌므로, 상태 전환·진행률 갱신·delivery 행 저장만 이 별도 빈의 짧은 트랜잭션에 위임한다
// (CLAUDE.md "외부 호출은 트랜잭션 밖" + self-invocation 회피 — async 빈에서 직접 @Transactional 호출은 proxy 우회).
@Service
class AnnouncementProgressWriter(
    private val announcementRepository: AnnouncementRepository,
    private val deliveryRepository: AnnouncementDeliveryRepository,
    private val userDeviceService: UserDeviceService,
) {
    // 발송할 내용 + 대상 수신자. claim 이 성공하면 SENDING 으로 전환된 상태다.
    data class Claim(
        val title: String,
        val body: String,
        val recipients: List<UUID>,
    )

    // SENDING 클레임 — DRAFT/SCHEDULED 일 때만 SENDING 으로 전환하고 대상자를 못 박는다(진행률 분모).
    // 이미 처리 중·완료거나 없는 공지면 null(중복 발송 차단). 단일 인스턴스 가정 — 다중 인스턴스 동시 클레임 방어는 후속(SELECT FOR UPDATE 등).
    @Transactional
    fun claim(
        announcementId: Long,
        actor: String,
    ): Claim? {
        val announcement = announcementRepository.findById(announcementId).orElse(null) ?: return null
        if (!(announcement.isDraft || announcement.isScheduled)) return null
        val recipients = userDeviceService.findAllTokenHolderIds()
        announcement.markSending(recipientCount = recipients.size, sentBy = actor)
        announcementRepository.save(announcement)
        return Claim(announcement.title, announcement.body, recipients)
    }

    // 배치 flush — delivery 건별 행 저장 + 진행률 누적. fan-out 중 주기적으로 호출돼 폴링이 진행률을 본다.
    @Transactional
    fun flush(
        announcementId: Long,
        deliveries: List<AnnouncementDelivery>,
        successDelta: Int,
        failureDelta: Int,
        skippedDelta: Int,
    ) {
        deliveryRepository.saveAll(deliveries)
        val announcement = announcementRepository.findById(announcementId).orElse(null) ?: return
        announcement.addProgress(successDelta, failureDelta, skippedDelta)
        announcementRepository.save(announcement)
    }

    // 마지막 배치 flush + SENT 고정.
    @Transactional
    fun finish(
        announcementId: Long,
        deliveries: List<AnnouncementDelivery>,
        successDelta: Int,
        failureDelta: Int,
        skippedDelta: Int,
    ) {
        deliveryRepository.saveAll(deliveries)
        val announcement = announcementRepository.findById(announcementId).orElse(null) ?: return
        announcement.addProgress(successDelta, failureDelta, skippedDelta)
        announcement.markSent()
        announcementRepository.save(announcement)
    }
}
