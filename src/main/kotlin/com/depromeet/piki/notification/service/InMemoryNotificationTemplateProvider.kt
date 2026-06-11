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
            NotificationType.TOURNAMENT_STARTED to
                NotificationTemplate(title = "\${actorName}님이 토너먼트를 시작했어요", body = ""),
            NotificationType.TOURNAMENT_PLAYED_FROM_LINK to
                NotificationTemplate(title = "\${actorName}님이 회원님 토너먼트를 플레이했어요", body = ""),
            NotificationType.TOURNAMENT_COMPLETED to
                NotificationTemplate(title = "\${actorName}님이 회원님 토너먼트를 완료했어요", body = ""),
            NotificationType.TOURNAMENT_RESULT_READY to
                NotificationTemplate(title = "참여하신 \${actorName}님의 토너먼트 결과가 나왔어요", body = ""),
            NotificationType.ITEM_PARSING_COMPLETED to
                NotificationTemplate(title = "상품 정보가 저장됐어요", body = ""),
            NotificationType.ITEM_PARSING_FAILED to
                NotificationTemplate(title = "상품 정보를 가져오지 못했어요", body = ""),
            // 공지 본문은 추후 운영 도구가 변수(title·body)로 채운다(#391/#250). 트리거 전까진 발송되지 않는 placeholder.
            NotificationType.ANNOUNCEMENT to
                NotificationTemplate(title = "\${title}", body = "\${body}"),
        )

    // 시작 시점 전수 검증 — enum 에 새 타입을 추가하고 여기 시드를 빠뜨리면, 첫 발송(런타임)이 아니라
    // 빈 생성(앱 부팅) 시점에 즉시 깨져 누락을 드러낸다. find() 의 fail-fast 와 ParameterizedTest 로도
    // 잡지만, 부팅 차단이 운영에서 가장 이르게 발견된다.
    init {
        val missing = NotificationType.entries.filterNot { it in templates }
        require(missing.isEmpty()) { "템플릿 미등록: $missing" }
    }

    override fun find(type: NotificationType): NotificationTemplate = templates[type] ?: error("템플릿 미등록: $type")
}
