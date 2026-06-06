package com.depromeet.piki.admin.item

import com.depromeet.piki.admin.audit.AdminAuditService
import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.item.domain.ItemSnapshot
import com.depromeet.piki.item.domain.ItemStatus
import com.depromeet.piki.item.repository.ItemRepository
import com.depromeet.piki.item.repository.ItemSnapshotRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 개발 서버 백오피스의 item 실험 데이터 작업.
 *
 * LLM 없이 폼에서 직접 호출된다. 조회는 readOnly, 추가는 영속화 + 감사 기록을 한 트랜잭션으로 묶는다.
 * 범위·접두사 검증은 입력 경계(컨트롤러 폼)와 여기(require) 양쪽에 둔다 — 폼을 우회한 호출에도 불변식이 선다.
 *
 * item 은 정체성(link)만, 추출값·상태는 ItemSnapshot 이 보유하므로, 샘플도 item(정체성)과 READY snapshot 을 함께 만들고
 * 목록도 item + 최신 snapshot 을 묶어 보여준다.
 */
@Service
@ConditionalOnAdminEnabled
class AdminItemService(
    private val itemRepository: ItemRepository,
    private val itemSnapshotRepository: ItemSnapshotRepository,
    private val auditService: AdminAuditService,
) {
    @Transactional(readOnly = true)
    fun recentItems(limit: Int = DEFAULT_LIMIT): List<AdminItemView> {
        val items = itemRepository.findRecent(limit.coerceIn(1, MAX_LIMIT))
        return items.map { item -> AdminItemView.from(item, itemSnapshotRepository.findLatestByItemId(item.getId())) }
    }

    @Transactional
    fun insertSamples(
        count: Int,
        namePrefix: String?,
        adminUsername: String,
    ): Int {
        require(count in 1..MAX_COUNT) { "추가 개수는 1 이상 $MAX_COUNT 이하여야 합니다." }
        val prefix = (namePrefix?.trim()?.ifBlank { null } ?: DEFAULT_PREFIX).take(PREFIX_MAX_LENGTH)
        // item(정체성)을 먼저 저장하고, 추출이 끝난 READY snapshot 을 평행하게 만든다 (샘플은 이미 값이 채워진 버전).
        val items = itemRepository.saveAll(List(count) { Item() })
        val snapshots =
            items.mapIndexed { index, item ->
                ItemSnapshot(
                    itemId = item.getId(),
                    name = "$prefix ${index + 1}",
                    currentPrice = SAMPLE_PRICE,
                    currency = SAMPLE_CURRENCY,
                    status = ItemStatus.READY,
                    extractedAt = LocalDateTime.now(),
                )
            }
        itemSnapshotRepository.saveAll(snapshots)
        auditService.record(
            adminUsername = adminUsername,
            actionType = ACTION_INSERT_SAMPLES,
            toolName = "items",
            parameters = mapOf("count" to count, "namePrefix" to prefix),
            resultStatus = "SUCCESS",
            resultSummary = "inserted=${items.size}, ids=${items.map { it.getId() }}",
            requestMessage = null,
        )
        return items.size
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
