package com.depromeet.piki.notification.domain

import com.depromeet.piki.common.domain.LongBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.util.UUID

// 알림 내역. title/body 는 발송 시점에 템플릿 변수 치환이 끝난 완성본을 저장한다
// (템플릿 문구가 나중에 바뀌어도 과거 알림 표시는 발송 당시 그대로 유지된다).
// 채널(SSE/FCM)은 이 엔티티를 그대로 받아 전달하고, 읽음/badge 조회(#246)도 이 테이블을 본다.
@Entity
@Table(name = "notifications")
class Notification(
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    val userId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    val type: NotificationType,
    @Column(name = "title", nullable = false, length = 255)
    val title: String,
    @Column(name = "body", nullable = false, length = 255)
    val body: String,
    @Column(name = "ref_id", nullable = false)
    val refId: Long,
) : LongBaseEntity() {
    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false
        protected set

    // 읽음 처리. 멱등 — 이미 읽음이어도 재호출 무해. (읽음 API 는 #246)
    fun markRead() {
        isRead = true
    }
}
