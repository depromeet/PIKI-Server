package com.depromeet.piki.notification.handler

import com.depromeet.piki.item.event.ItemParsingCompleted
import com.depromeet.piki.notification.domain.NotificationType
import org.springframework.stereotype.Component
import java.util.UUID

// 아이템 파싱 완료 알림. 위시·토너먼트 어느 쪽으로 올린 아이템이든 동일하게 "파싱 완료" 를 알린다 —
// 수신자는 itemId 를 역조회해 위시 주인 ∪ 토너먼트 참가자로 모은다(ItemParsingRecipientResolver, 실패 알림과 공유).
// 변수 없는 고정 문구라 resolveVariables 는 베이스 기본값(emptyMap)을 그대로 쓴다.
@Component
class ItemParsingCompletedHandler(
    private val recipientResolver: ItemParsingRecipientResolver,
) : NotificationEventHandler<ItemParsingCompleted>(NotificationType.ITEM_PARSING_COMPLETED) {
    override fun resolveRefId(event: ItemParsingCompleted): Long = event.itemId

    override fun resolveRecipients(event: ItemParsingCompleted): Set<UUID> = recipientResolver.resolve(event.itemId)
}
