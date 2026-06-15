package com.depromeet.piki.common.exception

// description 은 fail(category) 의 fallback detail 로 클라이언트 응답에 그대로 나가는 사용자 대면 문구다
// (개발자용 분류 설명이 아니다). 401·403·검증 fallback·500 등에서 detail 미지정 시 이 문구가 응답된다.
// 분류의 의미 구분은 enum 이름이 담당하므로, 여기엔 사용자에게 보일 친화 문구만 둔다.
enum class ErrorCategory(
    val description: String,
) {
    INVALID_INPUT("다시 한번 확인해 주세요."),
    UNAUTHORIZED("로그인이 필요해요."),
    FORBIDDEN("접근할 수 없는 페이지예요."),
    NOT_FOUND("요청하신 정보를 찾을 수 없어요."),
    CONFLICT("요청을 처리하지 못했어요. 다시 확인해 주세요."),
    RETRYABLE("일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요."),
    SERVER_ERROR("일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요."),
}
