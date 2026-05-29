package com.depromeet.piki.admin.tool

import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import com.depromeet.piki.admin.exception.AdminChatException
import org.springframework.stereotype.Component

/**
 * 등록된 모든 [AdminTool] 빈의 화이트리스트.
 *
 * 새 tool 은 `@Component` 로 추가만 하면 자동 주입되어 레지스트리에 반영된다. gemini 패키지를 import 하지
 * 않도록 tool 목록만 노출하고, functionDeclaration 변환은 호출측(AdminChatService)이 담당한다.
 */
@Component
@ConditionalOnAdminEnabled
class AdminToolRegistry(
    tools: List<AdminTool>,
) {
    private val byName: Map<String, AdminTool> = tools.associateBy { it.name }

    init {
        // name 충돌은 화이트리스트가 조용히 덮어써지는 버그이므로 부팅 시점에 깬다.
        require(byName.size == tools.size) { "admin tool name 이 중복되었다: ${tools.map { it.name }}" }
    }

    fun all(): Collection<AdminTool> = byName.values

    fun find(name: String): AdminTool = byName[name] ?: throw AdminChatException.unknownTool()
}
