package com.depromeet.piki.notification.service

import com.depromeet.piki.notification.domain.Notification
import com.depromeet.piki.notification.handler.NotificationEventHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

// 도메인 이벤트를 핸들러로 라우팅하고, 수신자별로 알림을 저장한 뒤 모든 채널로 전달한다.
// 도메인 이벤트의 종류를 모른다 — when 분기 없이 handlers 맵으로만 라우팅하므로, 새 이벤트가 늘어도 이 클래스는 불변이다.
@Component
class NotificationDispatcher(
    handlers: List<NotificationEventHandler<*>>,
    private val templateProvider: NotificationTemplateProvider,
    private val renderer: NotificationTemplateRenderer,
    private val persistence: NotificationPersistenceService,
    private val channels: List<NotificationChannel>,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val byType: Map<KClass<*>, NotificationEventHandler<*>> =
        handlers.associateBy { it.eventType }

    fun dispatch(event: Any) {
        // 타입 캐스팅은 여기 한 곳에 격리한다 — byType 매칭이 eventType 과 event::class 의 일치를 보장하므로 안전.
        @Suppress("UNCHECKED_CAST")
        val handler =
            byType[event::class] as? NotificationEventHandler<Any>
                ?: error("핸들러 미등록: ${event::class.simpleName}")

        val recipients = handler.resolveRecipients(event)
        if (recipients.isEmpty()) return

        val refId = handler.resolveRefId(event)
        val template = templateProvider.find(handler.notificationType)
        val variables = handler.resolveVariables(event)
        val title = renderer.render(template.title, variables)
        val body = renderer.render(template.body, variables)

        recipients.forEach { userId ->
            // 한 수신자의 저장 실패가 나머지 수신자 fan-out 을 막지 않게 수신자 단위로 격리한다 (외부 전달은 트랜잭션 밖).
            runCatching {
                val notification =
                    persistence.save(
                        Notification(
                            userId = userId,
                            type = handler.notificationType,
                            title = title,
                            body = body,
                            refId = refId,
                        ),
                    )
                // 한 채널의 실패도 다른 채널 전달을 막지 않게 추가로 격리한다.
                channels.forEach { channel ->
                    runCatching { channel.send(userId, notification) }
                        .onFailure { e -> log.warn("채널 {} 전송 실패 userId={}", channel::class.simpleName, userId, e) }
                }
            }.onFailure { e -> log.warn("알림 저장 실패로 수신자 건너뜀 userId={}", userId, e) }
        }
    }
}
