package com.depromeet.piki.admin.audit

import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * admin 작업 감사 로그를 짧은 트랜잭션으로 영속화한다.
 *
 * 외부 호출(LLM)은 이미 AdminChatService 에서 트랜잭션 밖으로 끝났고, 여기선 기록만 한다.
 * parameters 는 민감 키를 마스킹한 뒤 JSON 으로 저장한다.
 */
@Service
@ConditionalOnAdminEnabled
class AdminAuditService(
    private val repository: AdminAuditLogRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun record(
        adminUsername: String,
        actionType: String,
        toolName: String,
        parameters: Map<String, Any?>,
        resultStatus: String,
        resultSummary: String?,
        requestMessage: String?,
    ) {
        // 감사 기록은 best-effort 다 — 직렬화 실패(순환 참조·직렬화 불가 타입)가 비즈니스 트랜잭션(아이템
        // 저장 등)까지 롤백시키면 안 되므로, 실패 시 placeholder 로 대체하고 비즈니스는 그대로 진행시킨다.
        val maskedJson =
            try {
                objectMapper.writeValueAsString(mask(parameters))
            } catch (e: Exception) {
                log.warn("admin 감사 파라미터 직렬화 실패: action={} tool={}", actionType, toolName, e)
                AUDIT_SERIALIZATION_FAILED
            }
        repository.save(
            AdminAuditLog(
                adminUsername = adminUsername,
                actionType = actionType,
                toolName = toolName,
                resultStatus = resultStatus,
                parameters = maskedJson,
                resultSummary = resultSummary?.take(RESULT_SUMMARY_MAX),
                requestMessage = requestMessage?.take(REQUEST_MESSAGE_MAX),
            ),
        )
        log.info("admin audit: user={} action={} tool={} status={}", adminUsername, actionType, toolName, resultStatus)
    }

    // 키 이름에 민감 토큰이 섞이면 값을 가린다. 현재 tool 파라미터엔 민감정보가 없지만, 향후 tool 이 늘어
    // 토큰·URL 등이 섞여도 로그·DB 로 새지 않게 방어한다. 중첩 map/list 안의 값까지 재귀로 마스킹한다 —
    // 최상위 키만 보면 {config:{token:...}} 같은 중첩 비밀이 평문으로 새기 때문.
    private fun mask(value: Any?): Any? =
        when (value) {
            is Map<*, *> -> value.entries.associate { (key, nested) -> key to (if (isSensitive(key)) MASK else mask(nested)) }
            is List<*> -> value.map { mask(it) }
            else -> value
        }

    private fun isSensitive(key: Any?): Boolean {
        val name = key as? String ?: return false
        return SENSITIVE_KEYS.any { name.contains(it, ignoreCase = true) }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AdminAuditService::class.java)
        private const val MASK = "*secret*"
        private const val AUDIT_SERIALIZATION_FAILED = """{"error":"AUDIT_SERIALIZATION_FAILED"}"""
        private val SENSITIVE_KEYS = listOf("password", "token", "secret", "credential", "authorization")
        private const val RESULT_SUMMARY_MAX = 1000
        private const val REQUEST_MESSAGE_MAX = 2000
    }
}
