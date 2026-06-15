package com.depromeet.piki.admin.announcement

import com.depromeet.piki.common.domain.LongBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime

// 백오피스에서 작성·발송한 공지의 원본·발송 내역(#391/#489). 알림센터 fan-out 은 #489 가 별도로 다룬다.
@Entity
@Table(name = "announcements")
class Announcement(
    title: String,
    body: String,
    target: String,
) : LongBaseEntity() {
    @Column(name = "title", nullable = false, length = 255)
    var title: String = title
        protected set

    @Column(name = "body", nullable = false, length = 1000)
    var body: String = body
        protected set

    @Column(name = "target", nullable = false, length = 50)
    var target: String = target
        protected set

    @Column(name = "recipient_count", nullable = false)
    var recipientCount: Int = 0
        protected set

    @Column(name = "status", nullable = false, length = 20)
    var status: String = "DRAFT"
        protected set

    @Column(name = "sent_at")
    var sentAt: LocalDateTime? = null
        protected set

    // 발송한 운영자 — 등록(DRAFT) 시엔 null, 발송 시 채워진다.
    @Column(name = "sent_by", length = 50)
    var sentBy: String? = null
        protected set

    // 예약 발송 시각(#489) — SCHEDULED 상태일 때 채워지고, 스케줄러가 이 시각 도래 시 발송한다. 즉시 발송이면 null.
    @Column(name = "scheduled_at")
    var scheduledAt: LocalDateTime? = null
        protected set

    // 발송 진행률·집계(#489) — total 대비 success+failure 가 진행률. UI 는 이 집계만 보여준다(건별은 announcement_deliveries).
    @Column(name = "total_count", nullable = false)
    var totalCount: Int = 0
        protected set

    @Column(name = "success_count", nullable = false)
    var successCount: Int = 0
        protected set

    @Column(name = "failure_count", nullable = false)
    var failureCount: Int = 0
        protected set

    // 미도달(토큰 없음 · FCM 미설정) 수 — FCM 시도 자체가 불가했던 수신자. 실패(전송 시도 후 실패)와 구분한다.
    @Column(name = "skipped_count", nullable = false)
    var skippedCount: Int = 0
        protected set

    val isDraft: Boolean get() = status == STATUS_DRAFT
    val isScheduled: Boolean get() = status == STATUS_SCHEDULED
    val isSending: Boolean get() = status == STATUS_SENDING
    val isSent: Boolean get() = status == STATUS_SENT

    // 처리 완료 수(성공+실패+미도달). 진행률 분자다 — 토큰 없는 유저까지 다 처리해야 100%가 된다.
    val processedCount: Int get() = successCount + failureCount + skippedCount

    // 진행률 퍼센트(0~100). total 이 0이면(대상자 없음) 발송 완료 시 100 으로 본다.
    val progressPercent: Int
        get() = if (totalCount <= 0) (if (isSent) 100 else 0) else (processedCount * 100 / totalCount).coerceIn(0, 100)

    // 예약 설정 — DRAFT 에서만. 발송 시각을 못 박고 SCHEDULED 로. 스케줄러가 시각 도래 시 발송한다.
    fun schedule(at: LocalDateTime) {
        check(isDraft) { "예약은 DRAFT 상태에서만 가능하다. status=$status" }
        this.scheduledAt = at
        this.status = STATUS_SCHEDULED
    }

    // 예약 취소 — SCHEDULED → DRAFT 로 되돌려 다시 편집·삭제·재예약 가능하게 한다.
    fun cancelSchedule() {
        check(isScheduled) { "취소는 SCHEDULED 상태에서만 가능하다. status=$status" }
        this.scheduledAt = null
        this.status = STATUS_DRAFT
    }

    // 발송 시작 — DRAFT(즉시) 또는 SCHEDULED(예약 도래)에서 SENDING 으로. 대상자 수를 못 박아 진행률 분모로 쓴다.
    fun markSending(
        recipientCount: Int,
        sentBy: String,
    ) {
        check(isDraft || isScheduled) { "발송 시작은 DRAFT/SCHEDULED 에서만 가능하다. status=$status" }
        this.recipientCount = recipientCount
        this.totalCount = recipientCount
        this.successCount = 0
        this.failureCount = 0
        this.sentBy = sentBy
        this.status = STATUS_SENDING
    }

    // fan-out 진행 중 배치마다 집계를 누적한다(진행률 갱신).
    // 정상 흐름(서비스가 SENDING 인 공지에 올바른 델타만 넘김)에선 닿지 않는 불변식 — flush 중복 호출·음수 델타 등
    // 코드 버그가 집계를 오염시키는 걸 막는다(progressPercent 의 coerceIn 이 UI 만 가리고 저장값은 오염되던 구멍).
    fun addProgress(
        successDelta: Int,
        failureDelta: Int,
        skippedDelta: Int,
    ) {
        check(isSending) { "진행률 누적은 SENDING 상태에서만 가능하다. status=$status" }
        check(successDelta >= 0 && failureDelta >= 0 && skippedDelta >= 0) {
            "진행률 델타는 음수일 수 없다. success=$successDelta failure=$failureDelta skipped=$skippedDelta"
        }
        val nextProcessed = processedCount + successDelta + failureDelta + skippedDelta
        check(totalCount == 0 || nextProcessed <= totalCount) {
            "진행 누적이 대상 수를 초과한다. processed=$nextProcessed total=$totalCount"
        }
        this.successCount += successDelta
        this.failureCount += failureDelta
        this.skippedCount += skippedDelta
    }

    // 발송 완료 — SENDING → SENT 로 고정하고 완료 시각을 박는다(이후 원본·집계 불변).
    fun markSent() {
        check(isSending) { "완료 처리는 SENDING 상태에서만 가능하다. status=$status" }
        this.status = STATUS_SENT
        this.sentAt = LocalDateTime.now()
    }

    companion object {
        const val STATUS_DRAFT = "DRAFT"
        const val STATUS_SCHEDULED = "SCHEDULED"
        const val STATUS_SENDING = "SENDING"
        const val STATUS_SENT = "SENT"
    }
}
