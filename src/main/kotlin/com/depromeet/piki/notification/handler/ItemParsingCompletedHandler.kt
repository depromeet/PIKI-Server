package com.depromeet.piki.notification.handler

import com.depromeet.piki.item.event.ItemParsingCompleted
import com.depromeet.piki.notification.domain.NotificationType
import org.springframework.stereotype.Component
import java.util.UUID

// 아이템 파싱 완료 알림. itemId 역조회로 수신자를 정한다.
@Component
class ItemParsingCompletedHandler : NotificationEventHandler<ItemParsingCompleted> {
    override val eventType = ItemParsingCompleted::class
    override val notificationType = NotificationType.ITEM_PARSING_COMPLETED

    override fun resolveRefId(event: ItemParsingCompleted): Long = event.itemId

    // TODO(#236 수신자 정책 합의): itemId 역조회로 수신자 결정 후 구현.
    // owner-only / 참가자 fan-out / 파싱 요청자 본인 중 어느 정책인지 Epic·#212 와 충돌 상태라 보류한다.
    // 정책 미확정이라 현재는 빈 리스트 — 발행돼도 Dispatcher 가 빈 결과면 조용히 종료해 알림이 생기지 않는다.
    override fun resolveRecipients(event: ItemParsingCompleted): List<UUID> = emptyList()

    override fun resolveVariables(event: ItemParsingCompleted): Map<String, String> = emptyMap()
}
