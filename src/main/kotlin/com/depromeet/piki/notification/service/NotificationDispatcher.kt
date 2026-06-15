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

    // associateBy 는 eventType 충돌 시 마지막 핸들러로 조용히 덮어쓴다 — 같은 이벤트에 핸들러를 둘
    // 등록하면 한쪽 라우팅이 소리 없이 사라진다. 부팅 시점에 중복을 fail-fast 로 드러낸다.
    init {
        val duplicated = handlers.groupBy { it.eventType }.filterValues { it.size > 1 }.keys
        require(duplicated.isEmpty()) { "eventType 중복 핸들러 등록: $duplicated" }
    }

    fun dispatch(event: Any) {
        // 타입 캐스팅은 여기 한 곳에 격리한다 — byType 매칭이 eventType 과 event::class 의 일치를 보장하므로 안전.
        @Suppress("UNCHECKED_CAST")
        val handler =
            byType[event::class] as? NotificationEventHandler<Any>
                ?: error("핸들러 미등록: ${event::class.simpleName}")

        val recipients = handler.resolveRecipients(event)
        // 이벤트 수신 → 수신자 도출(인원). 디스패치는 async 워커라 MDC userId 는 이벤트를 유발한 actor 의 것이고,
        // 수신자는 actor 와 다른 유저들이라 수신자 userId 는 아래 fan-out 에서 명시적으로 남긴다.
        log.info("알림 디스패치 type={} event={} 수신자={}명", handler.notificationType, event::class.simpleName, recipients.size)
        if (recipients.isEmpty()) return

        val refId = handler.resolveRefId(event)
        val routing = handler.resolveRouting(event)
        // actor 는 한 이벤트에 한 명이라 수신자 루프 밖에서 한 번만 해석해(actorId 조회 1회) 모든 수신자 알림에 같은 값을 박는다(#473).
        // 변수(actorName)와 프사 snapshot 을 한 컨텍스트로 함께 받는다.
        val actorContext = handler.resolveActorContext(event)
        val actorImageUrl = actorContext.imageUrl
        val template = templateProvider.find(handler.notificationType)
        val variables = actorContext.variables
        val title = renderer.render(template.title, variables)
        val body = renderer.render(template.body, variables)

        var delivered = 0
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
                            routing = routing,
                            actorImageUrl = actorImageUrl,
                        ),
                    )
                // 한 채널의 실패도 다른 채널 전달을 막지 않게 추가로 격리한다.
                channels.forEach { channel ->
                    runCatching { channel.send(userId, notification) }
                        .onFailure { e -> log.warn("채널 {} 전송 실패 userId={}", channel::class.simpleName, userId, e) }
                }
            }.onFailure { e -> log.warn("알림 저장 실패로 수신자 건너뜀 userId={}", userId, e) }
                .onSuccess { delivered++ }
        }
        // fan-out 결과 요약 — 저장 실패로 누락된 수신자가 있으면 (수신자 인원 > 저장성공)으로 드러난다.
        log.info("알림 fan-out 완료 type={} 수신자={}명 저장성공={}건", handler.notificationType, recipients.size, delivered)
    }
}
