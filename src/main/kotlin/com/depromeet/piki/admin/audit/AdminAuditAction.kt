package com.depromeet.piki.admin.audit

// 감사 대상 행위 코드. 기능 액션(템플릿 수정·공지 발송)과 접근 제어(ACCESS_*, 슬랙-IP 게이트 #526)를 함께 남긴다.
enum class AdminAuditAction {
    TEMPLATE_UPDATE,
    ANNOUNCEMENT_SEND,

    // 슬랙-IP 접근 게이트(#526) — 누가(슬랙명) 어느 IP 를 허용/해제했는지, 거부된 접근은 무엇인지.
    ACCESS_GRANTED,
    ACCESS_REVOKED,
    ACCESS_DENIED,
}
