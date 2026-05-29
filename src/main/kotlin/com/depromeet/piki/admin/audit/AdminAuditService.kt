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
        val maskedJson = objectMapper.writeValueAsString(mask(parameters))
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

    // 키 이름에 민감 토큰이 섞이면 값을 가린다. 현재 tool 파라미터엔 민감정보가 없지만,
    // 향후 tool 이 늘어 토큰·URL 등이 섞여도 로그·DB 로 새지 않게 방어한다.
    private fun mask(parameters: Map<String, Any?>): Map<String, Any?> =
        parameters.mapValues { (key, value) ->
            if (SENSITIVE_KEYS.any { key.contains(it, ignoreCase = true) }) MASK else value
        }

    companion object {
        private val log = LoggerFactory.getLogger(AdminAuditService::class.java)
        private const val MASK = "*secret*"
        private val SENSITIVE_KEYS = listOf("password", "token", "secret", "credential", "authorization")
        private const val RESULT_SUMMARY_MAX = 1000
        private const val REQUEST_MESSAGE_MAX = 2000
    }
}
