package com.depromeet.piki.common.logging

import java.security.MessageDigest

// 로그·예외 message 에 남기는 민감값 마스킹 (CLAUDE.md "민감 정보는 마스킹해서 찍는다").
// 원칙: 크리덴셜(토큰)·직접식별 PII(이메일·닉네임 원문)는 원문을 절대 싣지 않는다.
//
// userId(UUID)는 여기 대상이 아니다 — 크리덴셜이 아니고(알아도 권한 0) 내부 가명 식별자라, 상관추적 join 키로
// 그대로 찍는다. 마스킹은 "비밀 노출" 차단이 목적이고, userId 는 비밀이 아니다([[LoggingKeys]]).
object SensitiveData {
    // 토큰(JWT access/refresh·FCM 기기 토큰 등) → 원문 대신 SHA-256 앞 8 hex 지문 + 길이.
    // 같은 토큰은 같은 지문이라 "같은 토큰이 등록됐다 해제됐다"를 로그로 상관추적할 수 있고(예: FCM 등록/해제),
    // 지문에서 원문 복원·재사용은 불가하다. 전량 노출(탈취)·무지문(상관추적 불가) 사이의 균형점.
    fun maskToken(token: String?): String {
        token ?: return "absent"
        val fingerprint =
            MessageDigest
                .getInstance("SHA-256")
                .digest(token.toByteArray())
                .take(4)
                .joinToString("") { "%02x".format(it) }
        return "sha256:$fingerprint(len=${token.length})"
    }

    // 이메일 → 로컬파트 첫 글자 + 도메인 (a***@example.com). 원문은 직접식별 PII 라 저장 금지.
    // 도메인은 남겨 provider 분포 등 운영 분석엔 쓰되, 개인 식별로는 못 잇게 로컬파트를 가린다.
    fun maskEmail(email: String?): String {
        email ?: return "absent"
        val at = email.indexOf('@')
        if (at <= 0) return "***"
        return "${email.first()}***${email.substring(at)}"
    }
}
