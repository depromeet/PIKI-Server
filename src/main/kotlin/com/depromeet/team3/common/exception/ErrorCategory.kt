package com.depromeet.team3.common.exception

enum class ErrorCategory(
    val description: String,
) {
    INVALID_INPUT("입력 오류 — 요청을 수정하여 재시도"),
    UNAUTHORIZED("인증 필요 — 로그인 후 재시도"),
    FORBIDDEN("권한 없음 — 접근 불가"),
    NOT_FOUND("리소스 없음 — 존재하지 않는 대상"),
    CONFLICT("상태 충돌 — 입력을 바꿔도 해소되지 않음"),
    RETRYABLE("일시적 오류 — 동일 요청으로 재시도 가능"),
    SERVER_ERROR("서버 오류 — 재시도 불가, 고객센터 문의"),
}
