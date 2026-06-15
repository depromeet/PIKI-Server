package com.depromeet.piki.admin.domain

import com.depromeet.piki.common.domain.LongBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

// admin 의 민감 작업 감사 기록(로그인·템플릿 수정·공지 발송 등). 실유저에 푸시를 쏘는 도구라 "누가 언제 무엇을"을
// 영속 기록으로 남긴다. (2단계에서 이 기록을 슬랙 채널로도 미러링한다 — #526.)
@Entity
@Table(name = "admin_audit_logs")
class AdminAuditLog(
    actor: String,
    action: String,
    detail: String,
    clientIp: String?,
) : LongBaseEntity() {
    // 행위자(admin username) — 누가.
    @Column(name = "actor", nullable = false, length = 50)
    var actor: String = actor
        protected set

    // 행위 코드(LOGIN_SUCCESS·LOGIN_FAILURE·TEMPLATE_UPDATE·ANNOUNCEMENT_SEND 등) — 무엇을.
    @Column(name = "action", nullable = false, length = 50)
    var action: String = action
        protected set

    // 사람이 읽는 상세. 민감 원본값(토큰 등)은 담지 않는다.
    @Column(name = "detail", nullable = false, length = 500)
    var detail: String = detail
        protected set

    // 어디서 — 접속 IP(있으면). 미인증 로그인 실패 등엔 없을 수 있어 nullable.
    @Column(name = "client_ip", length = 45)
    var clientIp: String? = clientIp
        protected set
}
