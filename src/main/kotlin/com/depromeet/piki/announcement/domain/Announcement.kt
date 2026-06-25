package com.depromeet.piki.announcement.domain

import com.depromeet.piki.common.domain.LongBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.time.ZoneId

// 백오피스에서 작성·발송한 공지의 원본·발송 내역(#391/#489). 알림센터 fan-out 은 #489 가 별도로 다룬다.
@Entity
@Table(name = "announcements")
class Announcement(
    title: String,
    body: String,
    target: String,
    pushEnabled: Boolean = true,
    pushTitle: String = "",
    pushBody: String = "",
) : LongBaseEntity() {
    @Column(name = "title", nullable = false, length = 255)
    var title: String = title
        protected set

    // 공지 페이지 본문 — 마크다운 장문(#561). 알림(notifications.body=VARCHAR(255))엔 들어가지 않고, 푸시·알림센터는
    // 아래 pushTitle·pushBody 를 쓴다. TEXT 라 컬럼 길이 대신 MAX_BODY_LENGTH(sane cap)로 생성 시점에 막는다.
    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    var body: String = body
        protected set

    @Column(name = "target", nullable = false, length = 50)
    var target: String = target
        protected set

    // FCM 인터럽트 여부 토글(#561). 공지는 페이지·알림센터엔 항상 보이고, 이 값이 true 일 때만 FCM 푸시를 보낸다.
    @Column(name = "push_enabled", nullable = false)
    var pushEnabled: Boolean = pushEnabled
        protected set

    // 푸시·알림센터 전용 문구 — 마크다운 body 와 분리해 알림의 VARCHAR(255) 한도를 지킨다.
    // pushTitle 은 보통 공지 title 을 그대로 쓰되(admin 이 기본 채움) 푸시 톤으로 따로 쓸 수 있다. pushBody 는 빈값 허용.
    @Column(name = "push_title", nullable = false, length = 255)
    var pushTitle: String = pushTitle
        protected set

    @Column(name = "push_body", nullable = false, length = 255)
    var pushBody: String = pushBody
        protected set

    // 푸시·알림센터에 실제로 들어가는 문구. pushTitle 이 비면 공지 title 로 폴백한다 — admin 이 기본 채우지만,
    // 미설정·기존 데이터(마이그레이션 default '')를 방어해 빈 알림 title 이 나가지 않게 한다. pushBody 는 빈값 허용(푸시 title 만 표시).
    // 둘 다 ≤255(notifications 한도)는 생성 시 require 로 보장된다.
    val effectivePushTitle: String get() = pushTitle.ifBlank { title }
    val effectivePushBody: String get() = pushBody

    // 엔티티 불변식 — 최후의 보루(입력 경계가 먼저 거른다). 누가 어떤 경로(생성·수정)로 만들든 길이를 넘으면
    // DB 저장 시점이 아니라 그 시점에 깨지게 한다(데이터 truncation 방지). 생성(init)·수정(edit)이 같은 검증을 공유한다.
    init {
        validateFields(title, body, pushTitle, pushBody)
    }

    // 초안 수정 — DRAFT 에서만(발송된·예약된 공지는 내용 변경 불가, 발송 전 오타 교정·다듬기용 #561).
    // SCHEDULED 는 cancelSchedule 로 DRAFT 로 되돌린 뒤 수정한다. 검증은 생성과 동일(validateFields).
    fun edit(
        title: String,
        body: String,
        pushEnabled: Boolean,
        pushTitle: String,
        pushBody: String,
    ) {
        check(isDraft) { "공지 수정은 DRAFT 상태에서만 가능하다. status=$status" }
        validateFields(title, body, pushTitle, pushBody)
        this.title = title
        this.body = body
        this.pushEnabled = pushEnabled
        this.pushTitle = pushTitle
        this.pushBody = pushBody
    }

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
    val isMissed: Boolean get() = status == STATUS_MISSED

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
        this.sentAt = LocalDateTime.now(KST)
    }

    // 유예시간 넘긴 예약을 정리 — SCHEDULED → MISSED. 다운타임 등으로 예약시각을 한참 넘겨 도래했을 때,
    // 철 지난 공지(점심 이벤트가 저녁에)를 자동 발송하지 않고 미발송으로 닫는다. 재발송은 운영자 판단(복제 등).
    fun markMissed() {
        check(isScheduled) { "MISSED 처리는 SCHEDULED 상태에서만 가능하다. status=$status" }
        this.status = STATUS_MISSED
    }

    companion object {
        const val STATUS_DRAFT = "DRAFT"
        const val STATUS_SCHEDULED = "SCHEDULED"
        const val STATUS_SENDING = "SENDING"
        const val STATUS_SENT = "SENT"
        const val STATUS_MISSED = "MISSED"

        // 입력 상한. title 255(컬럼 길이). body 는 TEXT(마크다운 패치노트)라 컬럼 길이 대신 sane cap 으로 둔다
        // (TEXT 최대 65535 bytes 의 한참 안쪽). push_title·push_body 는 알림(notifications, VARCHAR(255))으로 들어가 255.
        const val MAX_TITLE_LENGTH = 255
        const val MAX_BODY_LENGTH = 10000
        const val MAX_PUSH_TEXT_LENGTH = 255

        // 공지 예약·발송 시각은 전부 KST 로 다룬다 — JVM 기본 TZ 는 UTC 라(application.yml 173) now() 를 그대로 쓰면
        // 운영자가 입력한 KST wall-clock(예약 12:00)과 9시간 어긋나 오발송한다. scheduledAt 은 KST wall-clock 으로 저장하고
        // 스케줄러·미래검증·완료시각도 now(KST)로 비교해, 입력·저장·표시·발송을 모두 KST 로 자기일관하게 둔다(전역 TZ 미변경).
        val KST: ZoneId = ZoneId.of("Asia/Seoul")

        // 생성(init)·수정(edit) 공용 검증 — 길이/공백 불변식의 단일 출처.
        private fun validateFields(
            title: String,
            body: String,
            pushTitle: String,
            pushBody: String,
        ) {
            require(title.isNotBlank()) { "공지 제목이 비어 있습니다." }
            require(title.length <= MAX_TITLE_LENGTH) { "공지 제목 길이가 ${MAX_TITLE_LENGTH}자를 초과했습니다." }
            require(body.length <= MAX_BODY_LENGTH) { "공지 본문 길이가 ${MAX_BODY_LENGTH}자를 초과했습니다." }
            require(pushTitle.length <= MAX_PUSH_TEXT_LENGTH) { "푸시 제목 길이가 ${MAX_PUSH_TEXT_LENGTH}자를 초과했습니다." }
            require(pushBody.length <= MAX_PUSH_TEXT_LENGTH) { "푸시 본문 길이가 ${MAX_PUSH_TEXT_LENGTH}자를 초과했습니다." }
        }
    }
}
