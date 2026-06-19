package com.depromeet.piki.admin.audit

import org.springframework.data.jpa.repository.JpaRepository

// 감사 기록 저장소. append-only — 기록만 쌓고 수정/삭제하지 않는다.
interface AdminAuditLogRepository : JpaRepository<AdminAuditLog, Long>
