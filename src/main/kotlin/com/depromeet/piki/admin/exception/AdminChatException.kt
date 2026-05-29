package com.depromeet.piki.admin.exception

import com.depromeet.piki.common.exception.BaseException
import com.depromeet.piki.common.exception.ErrorCategory

/**
 * admin 챗봇 처리 중 발생하는 예외.
 *
 * admin 은 자체 DTO(AdminChatResponse)로 응답하므로 컨트롤러가 직접 잡아 변환한다. message 에는 LLM 원문·
 * 사용자 입력 원본을 담지 않고 고정 문구만 둔다(노출 안전). 구체 정보는 로그·cause 로 남긴다.
 */
class AdminChatException private constructor(
    message: String,
    val category: ErrorCategory,
    cause: Throwable? = null,
) : BaseException(message, cause) {
    companion object {
        // 모델이 화이트리스트 밖 tool 명을 환각. 정상 클라가 도달 불가하지만 외부 LLM 이 트리거라 서버측 신호.
        fun unknownTool(): AdminChatException = AdminChatException("알 수 없는 도구가 호출되었습니다.", ErrorCategory.SERVER_ERROR)

        fun tooManyTurns(): AdminChatException = AdminChatException("대화 처리 단계 한도를 초과했습니다.", ErrorCategory.SERVER_ERROR)

        fun budgetExceeded(): AdminChatException =
            AdminChatException("처리 시간이 초과되었습니다. 다시 시도해 주세요.", ErrorCategory.RETRYABLE)

        // 승인 토큰이 만료/소비됨. 클라가 정상 흐름에서 도달 가능한 계약 응답.
        fun pendingExpired(): AdminChatException =
            AdminChatException("확인 대기 작업을 찾을 수 없거나 만료되었습니다.", ErrorCategory.CONFLICT)

        fun emptyResponse(): AdminChatException = AdminChatException("LLM 응답을 해석할 수 없습니다.", ErrorCategory.SERVER_ERROR)
    }
}
