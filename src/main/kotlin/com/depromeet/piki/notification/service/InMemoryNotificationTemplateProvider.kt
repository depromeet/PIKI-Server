package com.depromeet.piki.notification.service

import com.depromeet.piki.notification.domain.NotificationType
import org.springframework.stereotype.Component

// 토대용 임시 Provider — enum 별 기본 문구를 코드에 둔다.
// #252 머지 시 DbNotificationTemplateProvider(같은 인터페이스)로 교체되며 이 빈은 제거된다.
@Component
class InMemoryNotificationTemplateProvider : NotificationTemplateProvider {
    private val templates: Map<NotificationType, NotificationTemplate> =
        mapOf(
            NotificationType.TOURNAMENT_JOINED to
                NotificationTemplate(title = "\${actorName}님이 참가했어요", body = ""),
            NotificationType.TOURNAMENT_ITEM_ADDED to
                NotificationTemplate(title = "\${actorName}님이 아이템을 추가했어요", body = ""),
            NotificationType.ITEM_PARSING_COMPLETED to
                NotificationTemplate(title = "상품 정보가 저장됐어요", body = ""),
            NotificationType.ITEM_PARSING_FAILED to
                NotificationTemplate(title = "상품 정보를 가져오지 못했어요", body = ""),
        )

    // enum 에 새 타입을 추가하고 여기 시드를 빠뜨리면 발송 시점에 즉시 깨져 누락을 드러낸다.
    override fun find(type: NotificationType): NotificationTemplate = templates[type] ?: error("템플릿 미등록: $type")
}
