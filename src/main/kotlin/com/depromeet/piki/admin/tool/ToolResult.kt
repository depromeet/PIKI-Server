package com.depromeet.piki.admin.tool

/**
 * tool 실행 결과.
 *
 * - [Executed]: read tool 이 즉시 실행을 마친 결과. functionResponse 로 모델에 돌려줄 payload.
 * - [PendingConfirmation]: write tool 이 부수효과 없이 검증+미리보기만 한 상태. 사용자 승인 후 commit 으로 실행.
 * - [Failed]: 파라미터 검증 실패 등. 모델에 functionResponse 로 돌려 자가수정을 유도하거나 사용자에게 노출.
 */
sealed interface ToolResult {
    data class Executed(
        val payload: Map<String, Any?>,
    ) : ToolResult

    data class PendingConfirmation(
        val summary: String,
        val args: Map<String, Any?>,
    ) : ToolResult

    data class Failed(
        val reason: String,
    ) : ToolResult
}
