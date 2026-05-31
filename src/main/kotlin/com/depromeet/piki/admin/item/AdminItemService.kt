package com.depromeet.piki.admin.item

import com.depromeet.piki.admin.audit.AdminAuditService
import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.item.domain.ItemStatus
import com.depromeet.piki.item.repository.ItemRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 개발 서버 백오피스의 item 실험 데이터 작업.
 *
 * LLM 없이 폼에서 직접 호출된다. 조회는 readOnly, 추가는 영속화 + 감사 기록을 한 트랜잭션으로 묶는다.
 * 범위·접두사 검증은 입력 경계(컨트롤러 폼)와 여기(require) 양쪽에 둔다 — 폼을 우회한 호출에도 불변식이 선다.
 */
@Service
@ConditionalOnAdminEnabled
class AdminItemService(
    private val itemRepository: ItemRepository,
    private val auditService: AdminAuditService,
) {
    @Transactional(readOnly = true)
    fun recentItems(limit: Int = DEFAULT_LIMIT): List<Item> = itemRepository.findRecent(limit.coerceIn(1, MAX_LIMIT))

    @Transactional
    fun insertSamples(
        count: Int,
        namePrefix: String?,
        adminUsername: String,
    ): Int {
        require(count in 1..MAX_COUNT) { "추가 개수는 1 이상 $MAX_COUNT 이하여야 합니다." }
        val prefix = (namePrefix?.trim()?.ifBlank { null } ?: DEFAULT_PREFIX).take(PREFIX_MAX_LENGTH)
        val items =
            (1..count).map {
                Item(
                    name = "$prefix $it",
                    currentPrice = SAMPLE_PRICE,
                    currency = SAMPLE_CURRENCY,
                    status = ItemStatus.READY,
                )
            }
        val saved = itemRepository.saveAll(items)
        auditService.record(
            adminUsername = adminUsername,
            actionType = ACTION_INSERT_SAMPLES,
            toolName = "items",
            parameters = mapOf("count" to count, "namePrefix" to prefix),
            resultStatus = "SUCCESS",
            resultSummary = "inserted=${saved.size}, ids=${saved.map { it.getId() }}",
            requestMessage = null,
        )
        return saved.size
    }

    companion object {
        const val DEFAULT_LIMIT = 10
        private const val MAX_LIMIT = 50
        private const val MAX_COUNT = 50
        private const val DEFAULT_PREFIX = "샘플 상품"
        private const val PREFIX_MAX_LENGTH = 100
        private const val SAMPLE_PRICE = 10_000
        private const val SAMPLE_CURRENCY = "KRW"
        private const val ACTION_INSERT_SAMPLES = "INSERT_SAMPLE_ITEMS"
    }
}
