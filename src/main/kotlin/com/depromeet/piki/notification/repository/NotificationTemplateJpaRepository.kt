package com.depromeet.piki.notification.repository

import com.depromeet.piki.notification.domain.NotificationTemplateEntity
import com.depromeet.piki.notification.domain.NotificationType
import org.springframework.data.jpa.repository.JpaRepository

// 알림 템플릿 저장소(#252). 조회 요건이 사소(type 단건·전체)해 Spring Data 직접 사용. type 이 @Id 라 키 타입이 enum.
interface NotificationTemplateJpaRepository : JpaRepository<NotificationTemplateEntity, NotificationType>
