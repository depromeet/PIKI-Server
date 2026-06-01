package com.depromeet.piki.notification.service

import com.depromeet.piki.notification.domain.NotificationType

// 알림 타입별 템플릿 조회. 토대는 InMemory 구현으로 시작하고, #252 가 DB 구현으로 교체한다(인터페이스 동일).
interface NotificationTemplateProvider {
    fun find(type: NotificationType): NotificationTemplate
}
