package com.depromeet.piki.admin.audit

import org.springframework.data.repository.Repository

/**
 * append-only 감사 로그 저장소.
 *
 * JpaRepository 를 그대로 노출하면 delete*·재저장 같은 변경 API 가 열려 append-only 불변식이 깨진다 —
 * 호출부 하나만 늘어도 감사 로그가 삭제·변조될 수 있다. save 와 조회만 노출하는 좁은 Repository 로 제한한다.
 */
interface AdminAuditLogRepository : Repository<AdminAuditLog, Long> {
    fun save(entity: AdminAuditLog): AdminAuditLog

    fun findAll(): List<AdminAuditLog>
}
