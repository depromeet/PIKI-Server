package com.depromeet.piki.admin.tool.impl

import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import com.depromeet.piki.admin.tool.AdminTool
import com.depromeet.piki.admin.tool.ToolResult
import com.depromeet.piki.admin.tool.ToolSchema
import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.item.domain.ItemStatus
import com.depromeet.piki.item.repository.ItemRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 추가(write) tool — items 테이블에 실험용 샘플 상품 row 를 N개 넣는다.
 *
 * write 라 [execute] 는 검증+미리보기만 하고(부수효과 없음), 실제 영속화는 사용자 승인 후 [commit] 에서 한다.
 * [commit] 만 짧은 `@Transactional` 로 묶는다 — 외부 호출(LLM)은 이미 트랜잭션 밖에서 끝났고 여기선 영속화만 한다.
 */
@Component
@ConditionalOnAdminEnabled
class InsertSampleItemsTool(
    private val itemRepository: ItemRepository,
) : AdminTool {
    override val name: String = "insert_sample_items"
    override val description: String =
        "items 테이블에 실험용 샘플 상품 row 를 여러 개 추가한다. 테스트 데이터 시딩 용도이며 실제 외부 상품과 무관하다."
    override val isWrite: Boolean = true
    override val parameters: ToolSchema =
        ToolSchema.obj(
            properties =
                mapOf(
                    "count" to ToolSchema.integer("추가할 샘플 상품 개수 (1~$MAX_COUNT)"),
                    "namePrefix" to ToolSchema.string("샘플 상품명 접두사 (선택, 미지정 시 '$DEFAULT_PREFIX')"),
                ),
            required = listOf("count"),
        )

    override fun execute(args: Map<String, Any?>): ToolResult {
        val count = parseCount(args) ?: return ToolResult.Failed("count 는 1~$MAX_COUNT 범위의 정수여야 합니다.")
        val prefix = parsePrefix(args)
        return ToolResult.PendingConfirmation(
            summary = "items 테이블에 '$prefix' 샘플 상품 ${count}개를 추가합니다.",
            args = mapOf("count" to count, "namePrefix" to prefix),
        )
    }

    @Transactional
    override fun commit(args: Map<String, Any?>): Map<String, Any?> {
        // 승인 시점에 다시 검증한다 — execute 가 걸렀어야 하므로 여기 닿으면 흐름 버그(불변식).
        val count = parseCount(args) ?: error("commit 시점 count 검증 실패 — execute 에서 걸러졌어야 한다")
        val prefix = parsePrefix(args)
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
        val ids = saved.map { it.getId() }
        log.info("admin insert_sample_items: {}개 추가 (ids={})", saved.size, ids)
        return mapOf("inserted" to saved.size, "ids" to ids)
    }

    private fun parseCount(args: Map<String, Any?>): Int? {
        val raw = (args["count"] as? Number)?.toInt() ?: return null
        return if (raw in 1..MAX_COUNT) raw else null
    }

    private fun parsePrefix(args: Map<String, Any?>): String {
        val prefix = (args["namePrefix"] as? String)?.trim()?.ifBlank { null } ?: DEFAULT_PREFIX
        return prefix.take(PREFIX_MAX_LENGTH)
    }

    companion object {
        private val log = LoggerFactory.getLogger(InsertSampleItemsTool::class.java)
        private const val MAX_COUNT = 50
        private const val DEFAULT_PREFIX = "샘플 상품"
        private const val PREFIX_MAX_LENGTH = 100
        private const val SAMPLE_PRICE = 10_000
        private const val SAMPLE_CURRENCY = "KRW"
    }
}
