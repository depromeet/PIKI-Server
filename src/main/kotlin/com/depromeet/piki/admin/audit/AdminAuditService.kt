package com.depromeet.piki.admin.audit

import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import com.depromeet.piki.admin.domain.AdminAuditLog
import com.depromeet.piki.admin.repository.AdminAuditLogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// admin 민감 작업 감사 기록. DB(append-only) + 구조화 로그(#497) 양쪽에 남긴다.
// 2단계(#526)에서 이 record() 지점에 슬랙 채널 알림을 얹는다.
@Service
@ConditionalOnAdminEnabled
class AdminAuditService(
    private val auditLogRepository: AdminAuditLogRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun record(
        actor: String,
        action: AdminAuditAction,
        detail: String,
        clientIp: String?,
    ) {
        auditLogRepository.save(AdminAuditLog(actor = actor, action = action.name, detail = detail, clientIp = clientIp))
        log.info("admin 감사 actor={} action={} ip={} detail={}", actor, action, clientIp, detail)
    }
}
