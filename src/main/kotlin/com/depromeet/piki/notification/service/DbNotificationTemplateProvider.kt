package com.depromeet.piki.notification.service

import com.depromeet.piki.notification.domain.NotificationType
import com.depromeet.piki.notification.repository.NotificationTemplateJpaRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

// DB 기반 템플릿 provider(#252) — InMemory 를 교체한다. 발송(dispatch)은 잦으므로 매번 DB 를 치지 않고 메모리 캐시로
// 읽고, 백오피스가 템플릿을 수정하면 reload() 로 캐시를 갱신한다. 부팅 시 전 타입 시드를 검증해(require) 누락을
// 런타임 발송이 아니라 부팅에서 드러낸다(InMemory 의 fail-fast 와 동일 보장).
@Component
class DbNotificationTemplateProvider(
    private val templateRepository: NotificationTemplateJpaRepository,
) : NotificationTemplateProvider {
    private val cache = ConcurrentHashMap<NotificationType, NotificationTemplate>()

    @PostConstruct
    fun load() {
        val loaded =
            templateRepository.findAll().associate {
                it.type to NotificationTemplate(title = it.titleTemplate, body = it.bodyTemplate)
            }
        val missing = NotificationType.entries.filterNot { it in loaded }
        require(missing.isEmpty()) { "notification_templates 시드 누락: $missing (Flyway 시드 확인)" }
        cache.clear()
        cache.putAll(loaded)
    }

    override fun find(type: NotificationType): NotificationTemplate = cache[type] ?: error("템플릿 미등록: $type")

    // 백오피스 수정 후 호출 — 캐시를 DB 최신으로 다시 채운다.
    fun reload() = load()
}
