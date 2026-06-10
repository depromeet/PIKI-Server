package com.depromeet.piki.admin.audit

import com.depromeet.piki.common.domain.LongBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * admin 백오피스가 실행한 작업의 감사 로그. append-only.
 *
 * admin 계정은 InMemory(users 테이블과 무관)라 admin_username 문자열로 기록하고 FK 를 두지 않는다.
 * parameters/request_message 는 마스킹을 거친 값만 저장한다(AdminAuditService).
 */
@Entity
@Table(name = "admin_audit_logs")
class AdminAuditLog(
    @Column(name = "admin_username", nullable = false, length = 100)
    val adminUsername: String,
    @Column(name = "action_type", nullable = false, length = 50)
    val actionType: String,
    @Column(name = "tool_name", nullable = false, length = 100)
    val toolName: String,
    @Column(name = "result_status", nullable = false, length = 20)
    val resultStatus: String,
    @Column(name = "parameters", columnDefinition = "JSON")
    val parameters: String? = null,
    @Column(name = "result_summary", length = 1000)
    val resultSummary: String? = null,
    @Column(name = "request_message", length = 2000)
    val requestMessage: String? = null,
) : LongBaseEntity()
