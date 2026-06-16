package com.depromeet.piki.admin.config

import jakarta.servlet.http.HttpServletRequest

// 접속자 실제 IP 추출 — 게이트(#526)의 IP allowlist 키라 위조되면 안 된다.
// nginx 는 X-Forwarded-For 를 `$proxy_add_x_forwarded_for`(append)로 넣어, 클라가 보낸 XFF 뒤에 실 IP 를 덧붙인다.
// 따라서 XFF '첫 hop' 은 클라가 위조한 값일 수 있다(허용된 IP 를 사칭해 게이트 우회 가능) → 쓰지 않는다.
// 대신 nginx 가 `$remote_addr`(실 연결 IP)로 '덮어쓰는' X-Real-IP 를 신뢰한다(앞단 ALB/CloudFront 없는 EIP 직결이라
// $remote_addr 이 진짜 클라 IP). nginx 가 없는 로컬·테스트는 remoteAddr 로 폴백.
object ClientIp {
    private const val REAL_IP = "X-Real-IP"

    fun of(request: HttpServletRequest): String =
        request
            .getHeader(REAL_IP)
            ?.trim()
            ?.ifBlank { null }
            ?: request.remoteAddr
}
