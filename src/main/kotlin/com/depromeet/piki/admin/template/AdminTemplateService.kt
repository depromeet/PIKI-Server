package com.depromeet.piki.admin.template

import com.depromeet.piki.admin.audit.AdminAuditAction
import com.depromeet.piki.admin.audit.AdminAuditService
import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import com.depromeet.piki.notification.domain.NotificationCategory
import com.depromeet.piki.notification.domain.NotificationType
import com.depromeet.piki.notification.repository.NotificationTemplateJpaRepository
import com.depromeet.piki.notification.service.DbNotificationTemplateProvider
import com.depromeet.piki.notification.service.NotificationTemplateRenderer
import com.depromeet.piki.notification.service.NotificationTemplateVariables
import com.depromeet.piki.notification.service.TemplateVariable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 백오피스 템플릿 관리(#250). 편집은 미래 발송에만 적용(렌더 결과는 발송 시점 freeze) — 여기선 템플릿 문자열만 바꾼다.
// 수정 시: 변수 검증(선언 안 된 변수 차단) → 저장 → provider 캐시 reload → 감사 기록.
@Service
@ConditionalOnAdminEnabled
class AdminTemplateService(
    private val templateRepository: NotificationTemplateJpaRepository,
    private val renderer: NotificationTemplateRenderer,
    private val templateProvider: DbNotificationTemplateProvider,
    private val auditService: AdminAuditService,
) {
    fun list(): List<TemplateView> =
        templateRepository
            .findAll()
            // ANNOUNCEMENT 는 재사용 "템플릿"이 아니라 매번 등록하는 공지 내용이다 — /admin/announcements 에서 관리하므로
            // 편집기 목록에서 제외한다(이벤트 알림 8종만 노출). DB 의 ${title}/${body} 행은 dispatch passthrough 로 남는다.
            .filter { it.type != NotificationType.ANNOUNCEMENT }
            .sortedBy { it.type.ordinal }
            .map { entity ->
            TemplateView(
                type = entity.type,
                category = NotificationCategory.of(entity.type),
                title = entity.titleTemplate,
                body = entity.bodyTemplate,
                variables = NotificationTemplateVariables.availableFor(entity.type),
            )
        }

    fun get(type: NotificationType): TemplateView {
        val entity = templateRepository.findById(type).orElseThrow { IllegalStateException("템플릿 없음: $type") }
        return TemplateView(
            type = entity.type,
            category = NotificationCategory.of(entity.type),
            title = entity.titleTemplate,
            body = entity.bodyTemplate,
            variables = NotificationTemplateVariables.availableFor(entity.type),
        )
    }

    @Transactional
    fun update(
        type: NotificationType,
        title: String,
        body: String,
        actor: String,
        clientIp: String?,
    ) {
        validateVariables(type, title, body)
        val entity = templateRepository.findById(type).orElseThrow { IllegalStateException("템플릿 없음: $type") }
        entity.update(title, body)
        templateRepository.save(entity)
        templateProvider.reload() // 캐시 갱신 → 다음 발송부터 새 문구
        auditService.record(actor, AdminAuditAction.TEMPLATE_UPDATE, "$type 템플릿 수정", clientIp)
    }

    // 실시간 미리보기 — 샘플값으로 렌더한 결과 + 선언 안 된 변수(있으면 경고).
    fun preview(
        type: NotificationType,
        title: String,
        body: String,
    ): TemplatePreview {
        val samples = NotificationTemplateVariables.sampleValues(type)
        val unknown = NotificationTemplateVariables.usedIn(title, body) - NotificationTemplateVariables.names(type)
        return TemplatePreview(
            title = renderer.render(title, samples),
            body = renderer.render(body, samples),
            category = NotificationCategory.of(type),
            unknownVariables = unknown.toList(),
        )
    }

    private fun validateVariables(
        type: NotificationType,
        title: String,
        body: String,
    ) {
        val unknown = NotificationTemplateVariables.usedIn(title, body) - NotificationTemplateVariables.names(type)
        require(unknown.isEmpty()) { "이 타입에 없는 변수: ${unknown.joinToString(", ") { "\${$it}" }}" }
    }
}

data class TemplateView(
    val type: NotificationType,
    val category: NotificationCategory,
    val title: String,
    val body: String,
    val variables: List<TemplateVariable>,
)

data class TemplatePreview(
    val title: String,
    val body: String,
    val category: NotificationCategory,
    val unknownVariables: List<String>,
)
