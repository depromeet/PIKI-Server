package com.depromeet.piki.notification.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

// 알림 타입별 문구 템플릿(#252). 백오피스(#250)가 배포 없이 title/body·푸시 발송 여부(push_enabled)를 수정한다. type 이 자연키(PK).
// 렌더 변수(${actorName} 등)의 카탈로그는 코드가 SSOT 라 여기 두지 않고, 편집 대상 문자열만 보관한다.
@Entity
@Table(name = "notification_templates")
class NotificationTemplateEntity(
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 64)
    val type: NotificationType,
    titleTemplate: String,
    bodyTemplate: String,
    pushEnabled: Boolean,
) {
    @Column(name = "title_template", nullable = false, length = 255)
    var titleTemplate: String = titleTemplate
        protected set

    @Column(name = "body_template", nullable = false, length = 500)
    var bodyTemplate: String = bodyTemplate
        protected set

    // 이 타입을 OS 푸시(FCM)까지 보낼지(백오피스 토글). false 면 SSE·알림센터만 가고 OS 트레이 푸시는 생략한다.
    @Column(name = "push_enabled", nullable = false)
    var pushEnabled: Boolean = pushEnabled
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
        protected set

    fun update(
        titleTemplate: String,
        bodyTemplate: String,
        pushEnabled: Boolean,
    ) {
        this.titleTemplate = titleTemplate
        this.bodyTemplate = bodyTemplate
        this.pushEnabled = pushEnabled
        this.updatedAt = LocalDateTime.now()
    }
}
