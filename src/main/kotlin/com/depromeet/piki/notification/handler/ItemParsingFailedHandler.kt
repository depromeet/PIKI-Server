package com.depromeet.piki.notification.handler

import com.depromeet.piki.item.event.ItemParsingFailed
import com.depromeet.piki.notification.domain.NotificationType
import org.springframework.stereotype.Component
import java.util.UUID

// 아이템 파싱 실패 알림. 수신자 규칙은 완료 알림과 동일하다.
@Component
class ItemParsingFailedHandler : NotificationEventHandler(
    ItemParsingFailed::class,
    NotificationType.ITEM_PARSING_FAILED,
) {
    override fun resolveRefId(event: Any): Long = (event as ItemParsingFailed).itemId

    // TODO(#236 수신자 정책 합의): itemId 역조회로 수신자 결정 후 구현. (ItemParsingCompletedHandler 와 동일 보류 사유)
    override fun resolveRecipients(event: Any): List<UUID> = emptyList()

    // 변수 없는 알림 — resolveVariables 는 베이스 기본값(emptyMap)을 그대로 쓴다.
}
