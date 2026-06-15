package com.depromeet.piki.admin.config

import jakarta.servlet.http.HttpServletRequest

// 접속자 실제 IP 추출. 앱은 nginx 뒤에서 127.0.0.1 바인딩이라(deploy.yml) nginx 가 넣는 X-Forwarded-For 가
// 신뢰 가능하다(외부가 앱에 직접 못 닿아 위조 불가). 첫 hop(최초 클라)이 실제 IP 다. 없으면 remoteAddr 폴백.
// (2단계 #526 의 IP allowlist 필터도 같은 추출을 쓴다.)
object ClientIp {
    private const val FORWARDED_FOR = "X-Forwarded-For"

    fun of(request: HttpServletRequest): String =
        request
            .getHeader(FORWARDED_FOR)
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?.ifBlank { null }
            ?: request.remoteAddr
}
