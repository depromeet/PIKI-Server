package com.depromeet.piki.admin.feature

import com.depromeet.piki.admin.audit.AdminAuditService
import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 개발 서버 백오피스의 기능 요청 인박스 작업.
 *
 * 조회는 readOnly, 등록·상태변경은 영속화 + 감사 기록을 한 트랜잭션으로 묶는다. 외부 호출이 없어 트랜잭션 안에서 끝낸다.
 */
@Service
@ConditionalOnAdminEnabled
class AdminFeatureRequestService(
    private val repository: AdminFeatureRequestRepository,
    private val auditService: AdminAuditService,
) {
    @Transactional(readOnly = true)
    fun recent(limit: Int = DEFAULT_LIMIT): List<AdminFeatureRequest> =
        repository.findRecent(PageRequest.of(0, limit.coerceIn(1, MAX_LIMIT)))

    @Transactional
    fun create(
        title: String,
        adminUsername: String,
    ): AdminFeatureRequest {
        val saved = repository.save(AdminFeatureRequest.create(title, adminUsername))
        auditService.record(
            adminUsername = adminUsername,
            actionType = ACTION_CREATE,
            toolName = TOOL_NAME,
            parameters = mapOf("title" to saved.title),
            resultStatus = "SUCCESS",
            resultSummary = "id=${saved.getId()}",
            requestMessage = null,
        )
        return saved
    }

    @Transactional
    fun toggleStatus(
        id: Long,
        adminUsername: String,
    ) {
        // 정상 흐름은 목록의 토글 버튼으로만 닿지만, 다른 탭에서 이미 처리된 항목 등 경합으로는 없는 id 가 올 수 있다.
        // admin 도구라 require 결과(IllegalArgumentException)를 컨트롤러가 잡아 flash 로 보여준다.
        val request = repository.findById(id) ?: throw IllegalArgumentException("존재하지 않는 기능 요청입니다.")
        request.toggleStatus()
        // 명시 save 없이 managed 엔티티의 dirty checking 으로 트랜잭션 커밋 시 update 된다.
        auditService.record(
            adminUsername = adminUsername,
            actionType = ACTION_TOGGLE_STATUS,
            toolName = TOOL_NAME,
            parameters = mapOf("id" to id, "status" to request.status.name),
            resultStatus = "SUCCESS",
            resultSummary = "id=$id, status=${request.status}",
            requestMessage = null,
        )
    }

    companion object {
        const val DEFAULT_LIMIT = 50
        private const val MAX_LIMIT = 200
        private const val TOOL_NAME = "feature-requests"
        private const val ACTION_CREATE = "CREATE_FEATURE_REQUEST"
        private const val ACTION_TOGGLE_STATUS = "TOGGLE_FEATURE_REQUEST_STATUS"
    }
}
