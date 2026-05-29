package com.depromeet.piki.admin.tool.impl

import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import com.depromeet.piki.admin.tool.AdminTool
import com.depromeet.piki.admin.tool.ToolResult
import com.depromeet.piki.admin.tool.ToolSchema
import com.depromeet.piki.item.repository.ItemRepository
import org.springframework.stereotype.Component

/**
 * 조회(read) tool — 최근 생성된 item 을 createdAt 내림차순으로 본다.
 *
 * read 라 즉시 실행한다. admin 은 시스템 전역 조회라 소유권 검증이 없는 [ItemRepository] 를 직접 쓴다
 * (DevUserController 가 JpaRepository 를 직접 주입한 선례와 같은 결).
 */
@Component
@ConditionalOnAdminEnabled
class ListRecentItemsTool(
    private val itemRepository: ItemRepository,
) : AdminTool {
    override val name: String = "list_recent_items"
    override val description: String = "최근 생성된 item 을 createdAt 내림차순으로 조회한다. 등록된 상품 데이터를 확인할 때 쓴다."
    override val isWrite: Boolean = false
    override val parameters: ToolSchema =
        ToolSchema.obj(
            properties = mapOf("limit" to ToolSchema.integer("조회 개수 (1~$MAX_LIMIT, 미지정 시 $DEFAULT_LIMIT)")),
        )

    override fun execute(args: Map<String, Any?>): ToolResult {
        val limit = parseLimit(args)
        val rows =
            itemRepository.findRecent(limit).map {
                mapOf(
                    "id" to it.getIdOrNull(),
                    "name" to it.name,
                    "currentPrice" to it.currentPrice,
                    "currency" to it.currency,
                    "status" to it.status.name,
                )
            }
        return ToolResult.Executed(mapOf("items" to rows, "count" to rows.size))
    }

    private fun parseLimit(args: Map<String, Any?>): Int {
        val raw = (args["limit"] as? Number)?.toInt() ?: DEFAULT_LIMIT
        return raw.coerceIn(1, MAX_LIMIT)
    }

    companion object {
        private const val DEFAULT_LIMIT = 10
        private const val MAX_LIMIT = 50
    }
}
